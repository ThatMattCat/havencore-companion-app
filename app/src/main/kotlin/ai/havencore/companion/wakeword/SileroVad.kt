package ai.havencore.companion.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD ONNX wrapper.
 *
 * Used post-wake by [WakeCaptureSession] to detect end-of-utterance.
 * Loads `silero_vad.onnx` from `assets/wakeword/` (overridable per ctor).
 *
 * Auto-detects two state layouts:
 *  - Older / current snakers4 builds: split LSTM with `h` + `c` inputs and
 *    `hn` + `cn` outputs (each typically shaped [2, 1, 64]).
 *  - v5 unified builds: a single `state` input / `stateN` output (shape
 *    [2, 1, 128]).
 *
 * Tensor shapes come from the session's input metadata so the wrapper is
 * tolerant of variant builds. Dynamic dims (-1 / 0) are coerced to 1.
 * Frame size is fixed at [FRAME_SAMPLES_16K] for 16 kHz.
 *
 * If the IO signature doesn't match either layout, the wrapper logs and
 * runs stateless (all-zero state every frame) — degraded but not throwing,
 * so post-wake capture can still terminate on the [WakeCaptureSession]
 * hard cap rather than hanging.
 */
class SileroVad(
    context: Context,
    assetPath: String = DEFAULT_ASSET_PATH,
    private val sampleRate: Int = SAMPLE_RATE_HZ,
) : Closeable {

    private enum class Layout { SPLIT_HC, UNIFIED_STATE, NONE }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    private val inputName: String
    private val srInputName: String?
    private val outputName: String

    private val layout: Layout
    private val hInputName: String?
    private val cInputName: String?
    private val hOutputName: String?
    private val cOutputName: String?
    private val hShape: LongArray
    private val cShape: LongArray
    private val stateInputName: String?
    private val stateOutputName: String?
    private val stateShape: LongArray

    @Volatile private var hBuffer: FloatArray = FloatArray(0)
    @Volatile private var cBuffer: FloatArray = FloatArray(0)
    @Volatile private var stateBuffer: FloatArray = FloatArray(0)
    @Volatile private var degraded: Boolean = false

    init {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        session = env.createSession(bytes, OrtSession.SessionOptions())
        val inputs = session.inputNames.toList()
        val outputs = session.outputNames.toList()
        inputName = inputs.firstOrNull { it.equals("input", ignoreCase = true) }
            ?: inputs.first()
        srInputName = inputs.firstOrNull { it.equals("sr", ignoreCase = true) }
        outputName = outputs.firstOrNull { it.equals("output", ignoreCase = true) }
            ?: outputs.first()

        hInputName = inputs.firstOrNull { it.equals("h", ignoreCase = true) }
        cInputName = inputs.firstOrNull { it.equals("c", ignoreCase = true) }
        hOutputName = outputs.firstOrNull {
            it.equals("hn", ignoreCase = true) || it.equals("h_out", ignoreCase = true)
        }
        cOutputName = outputs.firstOrNull {
            it.equals("cn", ignoreCase = true) || it.equals("c_out", ignoreCase = true)
        }
        stateInputName = inputs.firstOrNull { it.equals("state", ignoreCase = true) }
        stateOutputName = outputs.firstOrNull {
            it.equals("stateN", ignoreCase = true) ||
                it.equals("state_out", ignoreCase = true)
        }

        layout = when {
            hInputName != null && cInputName != null &&
                hOutputName != null && cOutputName != null -> Layout.SPLIT_HC
            stateInputName != null && stateOutputName != null -> Layout.UNIFIED_STATE
            else -> Layout.NONE
        }

        hShape = if (layout == Layout.SPLIT_HC) shapeOf(hInputName!!) else longArrayOf()
        cShape = if (layout == Layout.SPLIT_HC) shapeOf(cInputName!!) else longArrayOf()
        stateShape = if (layout == Layout.UNIFIED_STATE) shapeOf(stateInputName!!)
        else longArrayOf()

        allocateState()

        Log.i(
            TAG,
            "silero loaded asset=$assetPath layout=$layout inputs=$inputs " +
                "outputs=$outputs hShape=${hShape.toList()} cShape=${cShape.toList()} " +
                "stateShape=${stateShape.toList()}",
        )
    }

    private fun shapeOf(name: String): LongArray {
        val info = session.inputInfo[name] ?: return longArrayOf()
        val tInfo = info.info as? TensorInfo ?: return longArrayOf()
        return tInfo.shape.map { if (it <= 0L) 1L else it }.toLongArray()
    }

    private fun productOf(shape: LongArray): Int {
        if (shape.isEmpty()) return 0
        var p = 1L
        for (d in shape) p *= d
        return p.toInt()
    }

    private fun allocateState() {
        hBuffer = FloatArray(productOf(hShape))
        cBuffer = FloatArray(productOf(cShape))
        stateBuffer = FloatArray(productOf(stateShape))
    }

    fun reset() {
        allocateState()
    }

    /**
     * Run one frame through the VAD. Frame must be exactly [FRAME_SAMPLES_16K]
     * samples for 16 kHz; shorter frames are zero-padded, longer are
     * truncated. Returns the speech probability in [0, 1]; 0f if degraded.
     */
    fun processFrame(frame: FloatArray): Float {
        if (degraded) return 0f
        val padded = when {
            frame.size == FRAME_SAMPLES_16K -> frame
            frame.size < FRAME_SAMPLES_16K -> frame.copyOf(FRAME_SAMPLES_16K)
            else -> frame.copyOfRange(0, FRAME_SAMPLES_16K)
        }
        return try {
            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs[inputName] = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(padded),
                longArrayOf(1L, padded.size.toLong()),
            )
            if (srInputName != null) {
                inputs[srInputName] = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(sampleRate.toLong())),
                    longArrayOf(),
                )
            }
            when (layout) {
                Layout.SPLIT_HC -> {
                    inputs[hInputName!!] =
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(hBuffer), hShape)
                    inputs[cInputName!!] =
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(cBuffer), cShape)
                }
                Layout.UNIFIED_STATE -> {
                    inputs[stateInputName!!] =
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(stateBuffer), stateShape)
                }
                Layout.NONE -> { /* stateless */ }
            }
            session.run(inputs).use { result ->
                val prob = extractScalar(result, outputName)
                when (layout) {
                    Layout.SPLIT_HC -> {
                        carryState(result, hOutputName!!, hBuffer)?.let { hBuffer = it }
                        carryState(result, cOutputName!!, cBuffer)?.let { cBuffer = it }
                    }
                    Layout.UNIFIED_STATE -> {
                        carryState(result, stateOutputName!!, stateBuffer)?.let {
                            stateBuffer = it
                        }
                    }
                    Layout.NONE -> { /* nothing to carry */ }
                }
                inputs.values.forEach { it.close() }
                prob
            }
        } catch (t: Throwable) {
            Log.e(TAG, "silero inference failed; degrading", t)
            degraded = true
            0f
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    private fun extractScalar(result: OrtSession.Result, name: String): Float {
        val tensor = result.get(name).orElse(null) as? OnnxTensor ?: return 0f
        return when (val v = tensor.value) {
            is FloatArray -> v.firstOrNull() ?: 0f
            is Array<*> -> firstFloat(v) ?: 0f
            else -> 0f
        }
    }

    private fun firstFloat(arr: Array<*>): Float? {
        for (e in arr) {
            when (e) {
                is FloatArray -> return e.firstOrNull()
                is Array<*> -> firstFloat(e)?.let { return it }
                is Float -> return e
            }
        }
        return null
    }

    private fun carryState(
        result: OrtSession.Result,
        name: String,
        scratch: FloatArray,
    ): FloatArray? {
        val tensor = result.get(name).orElse(null) as? OnnxTensor ?: return null
        return runCatching {
            val out = FloatArray(scratch.size)
            var i = 0
            fun walk(v: Any?) {
                when (v) {
                    is FloatArray -> for (x in v) { if (i < out.size) out[i++] = x }
                    is Array<*> -> for (c in v) walk(c)
                }
            }
            walk(tensor.value)
            out
        }.getOrNull()
    }

    companion object {
        private const val TAG = "WakeWord:Silero"
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_SAMPLES_16K = 512
        const val DEFAULT_ASSET_PATH = "wakeword/silero_vad.onnx"
    }
}
