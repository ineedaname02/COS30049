package com.example.myPlant.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ClassificationResult(
    val label: String,
    val confidence: Float
)

class MyAIClassifier(private val context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 256
    private val TAG = "MyModel"

    init {
        Log.d(TAG, "Initializing TFLite model...")

        val modelBuffer = FileUtil.loadMappedFile(context, "mobilenetv2_latest.tflite")
        interpreter = Interpreter(modelBuffer)
        labels = FileUtil.loadLabels(context, "labels.txt")

        Log.d(TAG, "✅ Model loaded successfully with ${labels.size} labels")
    }

    /**
     * Convert a bitmap into normalized float32 tensor input.
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        Log.d(TAG, "Preprocessing image for inference...")

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)
                val r = ((pixel shr 16 and 0xFF) / 255.0f)
                val g = ((pixel shr 8 and 0xFF) / 255.0f)
                val b = ((pixel and 0xFF) / 255.0f)
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }

        Log.d(TAG, "✅ Image preprocessing complete (${inputSize}x$inputSize)")
        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * Classify one bitmap image and return top label + confidence.
     */
    fun classify(bitmap: Bitmap): Pair<String, Float> {
        Log.d(TAG, "Running classification...")

        val inputBuffer = preprocessImage(bitmap)
        val outputBuffer = Array(1) { FloatArray(labels.size) }

        interpreter.run(inputBuffer, outputBuffer)

        val output = outputBuffer[0]
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: -1
        val confidence = output[maxIndex]
        val label = labels[maxIndex]

        Log.d(TAG, "✅ Prediction complete: label='$label', confidence=${"%.2f".format(confidence * 100)}%")

        return label to confidence
    }

    /**
     * Asynchronous classification for multiple images.
     */
    suspend fun classifyImages(uris: List<Uri>): List<ClassificationResult> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                try {
                    Log.d(TAG, "Processing image URI: $uri")
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    val (label, confidence) = classify(bitmap)
                    ClassificationResult(label, confidence)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error classifying image: ${e.message}", e)
                    null
                }
            }
        }
}
