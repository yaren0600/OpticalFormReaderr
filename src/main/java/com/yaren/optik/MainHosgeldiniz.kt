package com.yaren.optik

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yaren.optik.databinding.ActivityMainHosgeldinizBinding
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.w3c.dom.Document
import java.io.File
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MainHosgeldiniz : AppCompatActivity() {

    private lateinit var binding: ActivityMainHosgeldinizBinding
    val context = this
    val db = DatabaseHelper(context)

    private val getAnswerKeyImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processAndSaveAnswers(it, "answerkey.xml", "Cevap anahtarı kaydedildi.") }
    }

    private val getOpticalFormImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processAndSaveAnswers(it, "kullanici_cevap.xml", "Kullanıcı cevapları kaydedildi.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainHosgeldinizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // OpenCV başlatma kontrolü
        if (!OpenCVLoader.initDebug()) {
            showToast("OpenCV yüklenemedi! Uygulama kapanıyor.")
            finish()
            return
        }

        val preferences = getSharedPreferences("credentials", MODE_PRIVATE)
        val userId = preferences.getInt("userId", -1)

        if (userId != -1) {
            val user = db.getUserById(userId)
            if (user != null) {
                binding.kullaniciBilgi.text = "Kullanıcı Adı: ${user.username}"
                binding.kullaniciSifre.text = "Kullanıcı Şifresi: ${user.password}"
            } else {
                binding.kullaniciBilgi.text = "Kullanıcı bulunamadı."
                binding.kullaniciSifre.text = ""
            }
        }


        // Optik form işlemleri için buton tanımları
        binding.btnCikis.setOnClickListener {
            intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnAnswerKey.setOnClickListener {
            getAnswerKeyImage.launch("image/*")
        }

        binding.btnOpticalForm.setOnClickListener {
            getOpticalFormImage.launch("image/*")
        }

        binding.btnCompare.setOnClickListener {
            val resultText = compareAnswers()
            binding.tvResult.text = resultText
            binding.tvResult.visibility = View.VISIBLE
        }
        binding.btnSonuclariGoster.setOnClickListener {
            val intent = Intent(this, SonuclarActivity::class.java)
            startActivity(intent)
        }


    }

    private fun processAndSaveAnswers(imageUri: Uri, fileName: String, successMessage: String) {
        try {
            val answers = processOpticalForm(imageUri)
            saveAnswersToXml(fileName, answers)
            showToast(successMessage)
        } catch (e: Exception) {
            showToast("Bir hata oluştu: ${e.message}")
        }
    }

    private fun processOpticalForm(imageUri: Uri): List<String> {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        val mat = Mat()
        bitmapToMat(bitmap, mat)

        if (mat.empty()) throw IllegalArgumentException("Yüklenen görüntü işlenemedi.")

        val processedMat = preprocessImage(mat)
        saveMatAsImage(processedMat, "processed_form.jpg") // İşlenmiş görüntüyü kaydetme

        val boundingBoxes = findRectangles(processedMat)
        return boundingBoxes.map { rect -> detectAnswers(rect, processedMat)
        }
    }

    private fun preprocessImage(mat: Mat): Mat {
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val equalizedMat = Mat()
        Imgproc.equalizeHist(grayMat, equalizedMat)
        saveMatAsImage(equalizedMat, "eq.jpg")

        val blurredMat = Mat()
        Imgproc.GaussianBlur(equalizedMat, blurredMat, Size(5.0, 5.0), 0.0)
        saveMatAsImage(blurredMat, "blur.jpg")

        // Dilation (Genişletme)
        val dilatedMat = Mat()
        val dilationKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(blurredMat, dilatedMat, dilationKernel)
        saveMatAsImage(dilatedMat, "dilated.jpg")

        // Erosion (Aşındırma) - Gürültüyü Daha Fazla Azaltmak için
        val erodedMat = Mat()
        Imgproc.erode(dilatedMat, erodedMat, dilationKernel)
        saveMatAsImage(erodedMat, "eroded.jpg")

        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 100.0, 150.0)

        saveMatAsImage(edgesMat, "edges_detected.jpg") // Kenar tespit görüntüsünü kaydetme

        // Adaptif Eşikleme
        val adaptiveThreshMat = Mat()
        Imgproc.adaptiveThreshold(
            dilatedMat, adaptiveThreshMat,
            255.0, // Maksimum Piksel Değeri
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, // Gaussian Adaptif Eşikleme
            Imgproc.THRESH_BINARY_INV , // İkili (Binary) Eşikleme
            15, 3.0 // Blok Boyutu ve Sabit Değer
        )
        saveMatAsImage(adaptiveThreshMat, "adaptive_thresh.jpg")

        return adaptiveThreshMat
    }

    private fun saveMatAsImage(mat: Mat, fileName: String) {
        val file = File(filesDir, fileName)
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mat, bitmap)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        }
    }

    private fun findRectangles(edgesMat: Mat): List<Rect> {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edgesMat, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val rectangles = mutableListOf<Rect>()
        contours.forEach { contour ->
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            val epsilon = Imgproc.arcLength(contour2f, true) * 0.02
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

            if (approx.total() == 4L) {
                val rect = Imgproc.boundingRect(approx)
                if (rect.width > 50 && rect.height > 50) {
                    rectangles.add(rect)
                }
            }
        }
        return rectangles
    }

    private fun detectAnswers(boundingBox: Rect, contourMat: Mat): String {
        val options = listOf("A", "B", "C", "D", "E")
        val roi = Mat(contourMat, boundingBox)
        val columnWidth = roi.cols() / options.size

        val fillPercentages = options.indices.map { index ->
            val startX = index * columnWidth
            val endX = startX + columnWidth
            val columnRoi = roi.colRange(startX, endX)
            Core.countNonZero(columnRoi).toDouble() / (columnRoi.rows() * columnRoi.cols()) * 100
        }

        val thresholdPercentage = 10.0
        val maxPercentage = fillPercentages.maxOrNull() ?: 0.0
        val maxIndex = fillPercentages.indexOf(maxPercentage)

        return if (maxPercentage > thresholdPercentage && maxIndex in options.indices) {
            options[maxIndex]
        } else {
            "Boş"
        }
    }

    private fun bitmapToMat(bitmap: Bitmap, mat: Mat) {
        org.opencv.android.Utils.bitmapToMat(bitmap.copy(Bitmap.Config.ARGB_8888, true), mat)
    }

    private fun saveAnswersToXml(fileName: String, answers: List<String>) {
        val file = File(filesDir, fileName)
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document: Document = documentBuilder.newDocument()

        val rootElement = document.createElement("Answers")
        document.appendChild(rootElement)

        answers.forEachIndexed { index, answer ->
            rootElement.appendChild(document.createElement("Question").apply {
                setAttribute("number", (index + 1).toString())
                textContent = answer
            })
        }

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transform(DOMSource(document), StreamResult(file))
        }
    }

    private fun compareAnswers(): String {
        val answerKey = loadAnswersFromXml("answerkey.xml")
        val userAnswers = loadAnswersFromXml("kullanici_cevap.xml")

        val (correct, wrong, empty) = answerKey.indices.fold(Triple(0, 0, 0)) { acc, i ->
            when {
                i >= userAnswers.size || userAnswers[i].isEmpty() -> acc.copy(third = acc.third + 1)
                answerKey[i] == userAnswers[i] -> acc.copy(first = acc.first + 1)
                else -> acc.copy(second = acc.second + 1)
            }
        }

        return "Doğru: $correct, Yanlış: $wrong, Boş: $empty"
    }

    private fun loadAnswersFromXml(fileName: String): List<String> {
        val file = File(filesDir, fileName)
        if (!file.exists()) return emptyList()

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return document.getElementsByTagName("Question").let { nodeList ->
            List(nodeList.length) { nodeList.item(it).textContent }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
