package com.yaren.girissayfali

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yaren.girissayfali.databinding.ActivityMainHosgeldinizBinding
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory

class MainHosgeldiniz : AppCompatActivity() {

    private lateinit var binding: ActivityMainHosgeldinizBinding
    val context = this
    val db = DatabaseHelper(context)

    private val getAnswerKeyImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processAndSaveAnswers(it, "answerkey.xml", "Cevap anahtarı kaydedildi.") }
    }

    private val getOpticalFormImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processAndSaveAnswers(it, "user.xml", "Kullanıcı cevapları kaydedildi.") }
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
            val result = compareAnswers()

            // Sonucu ekranda göster (kitapçık ve sınav türünü de dahil edelim)
            binding.tvResult.text = """
                Sınav Türü: ${result.sinav}
                Kitapçık Türü: ${result.kitapcik}
                Doğru: ${result.dogru}
                Yanlış: ${result.yanlis}
                Boş: ${result.bos}
                """.trimIndent()
            binding.tvResult.visibility = View.VISIBLE

            // SharedPreferences'tan userId'yi al
            val preferences = getSharedPreferences("credentials", MODE_PRIVATE)
            val userId = preferences.getInt("userId", -1)

            if (userId == -1) {
                Toast.makeText(this, "Kullanıcı bilgisi alınamadı!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Veritabanına kaydet
            val db = DatabaseHelper(this)
            val inserted = db.insertResult(result, userId)

            if (inserted) {
                Toast.makeText(this, "Sonuç başarıyla kaydedildi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sonuç kaydedilemedi!", Toast.LENGTH_SHORT).show()
            }
        }




        binding.btnSonuclariGoster.setOnClickListener {
            val intent = Intent(this, SonuclarActivity::class.java)
            startActivity(intent)
        }


    }

    private fun processAndSaveAnswers(uri: Uri, fileName: String, successMessage: String) {
        try {
            processForm(uri, fileName) // ✅ xmlFileName parametresi de verildi
            showToast(successMessage)
        } catch (e: Exception) {
            showToast("Bir hata oluştu: ${e.message}")
        }
    }


    private fun processForm(uri: Uri, xmlFileName: String) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) // BURASI EKLENDİ

        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val mat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val preprocessed = preprocessImage(mat)
        val rectangles = detectRectangles(preprocessed)

        if (rectangles.isEmpty()) {
            showToast("Hiç dikdörtgen bulunamadı.")
            return
        }

        val labeledRegions = mutableMapOf<String, Rect>()
        val keywords = listOf("NUMARANIZ", "KİTAPÇIK", "SINAV", "DERS 1")
        var pending = rectangles.size

        rectangles.forEach { rect ->
            val roi = Mat(mat, rect)
            val roiBitmap = Bitmap.createBitmap(roi.cols(), roi.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(roi, roiBitmap)
            val image = InputImage.fromBitmap(roiBitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val ocrText = visionText.text.uppercase().replace("\n", " ")
                    keywords.forEach { keyword ->
                        if (ocrText.contains(keyword)) {
                            labeledRegions[keyword] = rect
                        }
                    }
                }
                .addOnCompleteListener {
                    pending--
                    if (pending == 0) {
                        finalizeRecognition(mat, labeledRegions, rectangles, xmlFileName)
                    }
                }
        }

    }





    private fun finalizeRecognition(
        mat: Mat,
        labeledRegions: Map<String, Rect>,
        rectangles: List<Rect>,
        xmlFileName: String
    ) {
        val labeledRects = mutableListOf<Pair<Rect, String>>()

        var studentNumber: String? = null
        var examType: String? = null
        var bookletType: String? = null
        var answers: List<Pair<Int, String>> = emptyList()

        // Numara
        labeledRegions["NUMARANIZ"]?.let {
            studentNumber = detectStudentNumber(mat, it)
            labeledRects.add(it to "Numara: $studentNumber")
        }

        // Kitapçık Türü
        labeledRegions["KİTAPÇIK"]?.let {
            bookletType = detectAndDrawBookletType(mat, it)
            labeledRects.add(it to "Kitapçık Türü: $bookletType")
        }

        // Sınav Türü
        labeledRegions["SINAV"]?.let {
            examType = detectAndDrawExamType(mat, it)
            labeledRects.add(it to "Sınav Türü: $examType")
        }

        // Cevap Alanı
        val ders1Rect = labeledRegions["DERS 1"]
        if (ders1Rect != null) {
            val answerArea = rectangles
                .filter { rect ->
                    rect.y > ders1Rect.y + ders1Rect.height &&
                            rect.x in (ders1Rect.x - 50)..(ders1Rect.x + ders1Rect.width + 50) &&
                            rect.height > ders1Rect.height * 1.5
                }
                .maxByOrNull { it.width * it.height }

            if (answerArea != null) {
                labeledRects.add(answerArea to "Cevap Alanı")

                val answerMat = Mat(mat, answerArea)
                val (detectedAnswers, markedAnswerMat) = analyzeAndMarkAnswers(answerMat)
                answers = detectedAnswers
                markedAnswerMat.copyTo(Mat(mat, answerArea))
            } else {
                showToast("Cevap alanı bulunamadı.")
            }
        } else {
            showToast("DERS 1 alanı bulunamadı.")
        }

        // ✅ Tek seferde tüm bilgileri XML'e kaydet
        saveAnswersToXml(xmlFileName, answers, studentNumber, examType, bookletType)

        // Görselleştir
        visualizeRegions(mat, labeledRects)
        showToast("$xmlFileName işlendi.")
        runOnUiThread {
            val studentNumberFromXml = readStudentNumberFromXml("user.xml")
            binding.kullaniciNumara.text = "Öğrenci Numarası: ${studentNumberFromXml ?: "?"}"
        }

    }


    private fun preprocessImage(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val thresh = Mat()
        Imgproc.adaptiveThreshold(blurred, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 15, 5.0)

        return thresh
    }


    private fun detectRectangles(binary: Mat): List<Rect> {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val height = binary.height()
        val width = binary.width()

        return contours.mapNotNull { contour ->
            val approx = MatOfPoint2f()
            val contour2f = MatOfPoint2f(*contour.toArray())
            val epsilon = Imgproc.arcLength(contour2f, true) * 0.02
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

            if (approx.total() == 4L) {
                val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))
                val area = rect.width * rect.height
                val aspect = rect.width.toDouble() / rect.height

                val isTooSmall = area < 10000
                val isTooLarge = area > 1000000
                val isWeirdRatio = aspect < 0.1 || aspect > 5.0

                val isTopRightCorner = rect.x > width * 0.70 && rect.y < height * 0.10
                val isBottomLeftCorner = rect.x < 900 && rect.y > height * 0.90 && aspect in 1.1..1.5 && area in 5000..8000

                if (isTopRightCorner || isBottomLeftCorner || isTooSmall || isTooLarge || isWeirdRatio) null else rect
            } else null
        }
    }


    private fun detectAndDrawExamType(mat: Mat, examRect: Rect): String {
        val examMat = Mat(mat, examRect)
        val gray = Mat()
        Imgproc.cvtColor(examMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.medianBlur(gray, gray, 5)

        // Hough Daire Dönüşümü
        val circles = Mat()
        Imgproc.HoughCircles(
            gray,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,               // dp
            20.0,              // minDist
            90.0,             // param1 (Canny edge)
            25.0,              // param2 (accumulator threshold)
            8, 20             // minRadius, maxRadius
        )

        if (circles.empty()) return "Belirsiz"

        val detectedCircles = mutableListOf<Triple<Point, Int, Double>>() // (center, radius, fillRatio)

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            val center = Point(data[0], data[1])
            val radius = data[2].toInt()

            val x = (center.x - radius).toInt().coerceAtLeast(0)
            val y = (center.y - radius).toInt().coerceAtLeast(0)
            val width = (radius * 2).coerceAtMost(examMat.cols() - x)
            val height = (radius * 2).coerceAtMost(examMat.rows() - y)

            if (x + width > examMat.cols() || y + height > examMat.rows()) continue

            val roi = Rect(x, y, width, height)
            val subMat = Mat(examMat, roi)

            val roiGray = Mat()
            Imgproc.cvtColor(subMat, roiGray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(roiGray, roiGray, Size(3.0, 3.0), 0.0)

            val binary = Mat()
            Imgproc.threshold(roiGray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

            val nonZero = Core.countNonZero(binary)
            val total = binary.rows() * binary.cols()
            val fillRatio = nonZero.toDouble() / total.toDouble()

            detectedCircles.add(Triple(center, radius, fillRatio))
        }

        // En dolu daireyi seç
        val threshold = 0.05
        val best = detectedCircles.maxByOrNull { it.third }
        if (best == null || best.third < threshold) return "Belirsiz"

        // Çizim (yeşil kutu)
        val bestCenter = best.first
        val bestRadius = best.second

        val topLeft = Point(examRect.x + bestCenter.x - bestRadius, examRect.y + bestCenter.y - bestRadius)
        val bottomRight = Point(examRect.x + bestCenter.x + bestRadius, examRect.y + bestCenter.y + bestRadius)

        Imgproc.rectangle(mat, topLeft, bottomRight, Scalar(0.0, 255.0, 0.0), 3)

        // Şık bir eşleme yapılabilir: Daire merkezlerinin Y konumuna göre seçeneği bul
        val y = bestCenter.y
        val index = when {
            y < 50 -> 0 // Kısa Sınav
            y < 90 -> 1 // Ara Sınav
            y < 130 -> 2 // Yıl Sonu
            else -> 3 // Bütünleme
        }

        val choices = listOf("Kısa Sınav", "Ara Sınav", "Yıl Sonu", "Bütünleme")
        return choices[index]
    }

    private fun detectAndDrawBookletType(mat: Mat, bookletRect: Rect): String {
        val bookletMat = Mat(mat, bookletRect)
        val gray = Mat()
        Imgproc.cvtColor(bookletMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.medianBlur(gray, gray, 5)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,
            20.0,
            90.0,
            25.0,
            8,
            20
        )

        if (circles.empty()) return "Belirsiz"

        val detectedCircles = mutableListOf<Triple<Point, Int, Double>>()

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            val center = Point(data[0], data[1])
            val radius = data[2].toInt()

            val x = (center.x - radius).toInt().coerceAtLeast(0)
            val y = (center.y - radius).toInt().coerceAtLeast(0)
            val width = (radius * 2).coerceAtMost(bookletMat.cols() - x)
            val height = (radius * 2).coerceAtMost(bookletMat.rows() - y)

            if (x + width > bookletMat.cols() || y + height > bookletMat.rows()) continue

            val roi = Rect(x, y, width, height)
            val subMat = Mat(bookletMat, roi)

            val roiGray = Mat()
            Imgproc.cvtColor(subMat, roiGray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(roiGray, roiGray, Size(3.0, 3.0), 0.0)

            val binary = Mat()
            Imgproc.threshold(roiGray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

            val nonZero = Core.countNonZero(binary)
            val total = binary.rows() * binary.cols()
            val fillRatio = nonZero.toDouble() / total.toDouble()

            detectedCircles.add(Triple(center, radius, fillRatio))
        }

        val threshold = 0.05
        val best = detectedCircles.maxByOrNull { it.third }
        if (best == null || best.third < threshold) return "Belirsiz"

        val bestCenter = best.first
        val bestRadius = best.second

        val topLeft = Point(bookletRect.x + bestCenter.x - bestRadius, bookletRect.y + bestCenter.y - bestRadius)
        val bottomRight = Point(bookletRect.x + bestCenter.x + bestRadius, bookletRect.y + bestCenter.y + bestRadius)
        Imgproc.rectangle(mat, topLeft, bottomRight, Scalar(255.0, 0.0, 0.0), 3)

        val y = bestCenter.y
        val index = when {
            y < 50 -> 0 // A
            y < 90 -> 1 // B
            y < 130 -> 2 // C
            else -> 3 // D
        }

        val choices = listOf("A", "B", "C", "D")
        return choices[index]
    }

    fun detectStudentNumber(mat: Mat, region: Rect, columnCount: Int = 10, rowCount: Int = 9): String {
        val regionMat = Mat(mat, region)
        val gray = Mat()
        Imgproc.cvtColor(regionMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(1.0, 1.0), 0.0)

        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(thresh.clone(), contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val bubbleRects = contours.map { Imgproc.boundingRect(it) }
            .filter {
                val ratio = it.width.toDouble() / it.height
                val area = it.width * it.height
                ratio in 0.4..1.5 && area in 200..1500
            }

        if (bubbleRects.isEmpty()) return ""

        // Baloncukları yukarıdan aşağıya ve soldan sağa sırala (önce y, sonra x)
        val sortedRects = bubbleRects.sortedWith(compareBy({ it.x },{it.y}))

        // Sütunlara göre grupla (örneğin her rakam için bir sütun)
        val columnTolerance = 8
        val groupedColumns = mutableListOf<MutableList<Rect>>()

        for (rect in sortedRects) {
            val centerX = rect.x + rect.width / 2
            val existingColumn = groupedColumns.find { column ->
                val refCenterX = column.first().x + column.first().width / 2
                Math.abs(centerX - refCenterX) < columnTolerance
            }

            if (existingColumn != null) {
                existingColumn.add(rect)
            } else {
                groupedColumns.add(mutableListOf(rect))
            }
        }

        // Her sütunu yukarıdan aşağıya sırala (0-9 rakamları için)
        groupedColumns.forEach { it.sortBy { rect -> rect.y } }

        val result = StringBuilder()

        for (column in groupedColumns.sortedBy { it.first().x }) {
            if (column.size < rowCount) {
                result.append("-")
                continue
            }

            val ratios = column.map { rect ->
                val roi = Mat(thresh, rect)
                Core.countNonZero(roi).toDouble() / (roi.rows() * roi.cols())
            }

            val maxRatio = ratios.maxOrNull() ?: 0.0
            val maxIndex = ratios.indexOf(maxRatio)
            val secondMax = ratios.filterIndexed { idx, _ -> idx != maxIndex }.maxOrNull() ?: 0.0

            val thresholdGap = 0.2
            val absoluteMinRatio = 0.3

            if (maxRatio > absoluteMinRatio && (maxRatio - secondMax) > thresholdGap) {
                result.append(maxIndex)
                // İşaretle (isteğe bağlı)
                Imgproc.rectangle(regionMat, column[maxIndex], Scalar(0.0, 255.0, 0.0), 2)
            } else {
                result.append("-")
            }
        }

        Log.d("StudentDetection", "Tespit edilen Numara: ${result}")
        return result.toString()
    }



    private fun analyzeAndMarkAnswers(answerMat: Mat): Pair<List<Pair<Int, String>>, Mat> {
        val gray = Mat()
        Imgproc.cvtColor(answerMat, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        val thresh = Mat()
        Imgproc.threshold(blurred, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Baloncuklara uygun dikdörtgenleri filtrele
        val bubbleRects = contours.mapNotNull { contour ->
            val rect = Imgproc.boundingRect(contour)
            val area = rect.width * rect.height
            val aspectRatio = rect.width.toDouble() / rect.height

            if (aspectRatio in 0.4..1.5 && area in 200..1500) rect else null
        }

        // Y koordinatına göre sırala
        val sortedRects = bubbleRects.sortedBy { it.y }

        // Satır gruplama
        val rowTolerance = 20
        val groupedRows = mutableListOf<MutableList<Rect>>()

        for (rect in sortedRects) {
            val centerY = rect.y + rect.height / 2
            val existingRow = groupedRows.find { group ->
                val refCenterY = group.first().y + group.first().height / 2
                Math.abs(centerY - refCenterY) < rowTolerance
            }
            if (existingRow != null) {
                existingRow.add(rect)
            } else {
                groupedRows.add(mutableListOf(rect))
            }
        }

        // Her satırdaki kutuları sola göre sırala
        for (row in groupedRows) {
            row.sortBy { it.x }
        }

        val outputImage = answerMat.clone()
        val results = mutableListOf<Pair<Int, String>>()

        var questionNumber = 1
        for (row in groupedRows) {
            if (row.size < 5) continue  // 5 şık şartı

            val fillRatios = row.take(5).map { rect ->
                val roi = Rect(rect.x, rect.y, rect.width, rect.height)
                val bubble = Mat(thresh, roi)
                Core.countNonZero(bubble).toDouble() / (bubble.rows() * bubble.cols())
            }

            val maxRatio = fillRatios.maxOrNull() ?: 0.0
            val maxIndex = fillRatios.indexOf(maxRatio)
            val secondMax = fillRatios.filterIndexed { index, _ -> index != maxIndex }.maxOrNull() ?: 0.0

            val thresholdGap = 0.2  // %20 fark şartı
            val selectedOption: String?
            val selectedRect: Rect?

            val absoluteMinRatio = 0.71
            if (maxRatio < absoluteMinRatio) {
                questionNumber++
                continue  // Bu satırda geçerli işaretleme yok
            }

            if (maxRatio > 0.6 && (maxRatio - secondMax) > thresholdGap) {
                selectedOption = ('A' + maxIndex).toString()
                selectedRect = row[maxIndex]

                results.add(Pair(questionNumber, selectedOption))

                // İşaretleme
                Imgproc.rectangle(
                    outputImage,
                    Point(selectedRect.x.toDouble(), selectedRect.y.toDouble()),
                    Point((selectedRect.x + selectedRect.width).toDouble(), (selectedRect.y + selectedRect.height).toDouble()),
                    Scalar(0.0, 255.0, 0.0), 2
                )
                Imgproc.putText(
                    outputImage,
                    "$questionNumber: $selectedOption",
                    Point(selectedRect.x.toDouble(), (selectedRect.y - 5).toDouble()),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.5,
                    Scalar(0.0, 255.0, 0.0), 1
                )
            }

            questionNumber++
        }

        return Pair(results, outputImage)
    }


    private fun saveAnswersToXml(
        fileName: String,
        answers: List<Pair<Int, String>>,
        studentNumber: String? = null,
        examType: String? = null,
        bookletType: String? = null
    ) {
        val file = File(filesDir, fileName)
        val builder = StringBuilder()

        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<OpticalForm>\n")

        // Öğrenci numarası varsa ekle
        studentNumber?.let {
            builder.append("\t<studentNumber>$it</studentNumber>\n")
        }

        // Sınav türü varsa ekle
        examType?.let {
            builder.append("\t<ExamType>$it</ExamType>\n")
        }

        // Kitapçık türü varsa ekle
        bookletType?.let {
            builder.append("\t<BookletType>$it</BookletType>\n")
        }

        builder.append("\t<Questions>\n")
        answers.forEach { (questionNo, answer) ->
            builder.append("\t\t<Question no=\"$questionNo\">$answer</Question>\n")
        }
        builder.append("\t</Questions>\n")

        builder.append("</OpticalForm>")

        file.writeText(builder.toString())
    }



    private fun visualizeRegions(src: Mat, regions: List<Pair<Rect, String>>) {
        val preview = src.clone()

        for ((rect, label) in regions) {
            Imgproc.putText(preview, label, Point(rect.x.toDouble(), rect.y.toDouble() - 10),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, Scalar(0.0, 255.0, 0.0), 2)
            Imgproc.rectangle(preview, rect, Scalar(0.0, 255.0, 0.0), 2)
        }

        val bitmap = Bitmap.createBitmap(preview.cols(), preview.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(preview, bitmap)

        runOnUiThread {
            binding.imageViewPreview.setImageBitmap(bitmap)
        }
    }

    private fun compareAnswers(): Sonuc {
        val keyAnswers = loadXmlAnswers("answerkey.xml")
        val userAnswers = loadXmlAnswers("user.xml")

        var correct = 0
        var wrong = 0
        var blank = 0

        for (i in keyAnswers.indices) {
            val userAns = userAnswers.getOrNull(i)
            when {
                userAns == null || userAns == "Boş" -> blank++
                userAns == keyAnswers[i] -> correct++
                else -> wrong++
            }
        }

        // user.xml dosyasından sınav türü ve kitapçık türünü oku
        val examType = loadXmlField("user.xml", "ExamType") ?: "İşaretlenmemiş"
        val bookletType = loadXmlField("user.xml", "BookletType") ?: "İşaretlenmemiş"

        return Sonuc(Date(), bookletType, examType, correct, wrong, blank )
    }
    private fun loadXmlField(fileName: String, fieldName: String): String? {
        return try {
            val file = File(filesDir, fileName)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            doc.documentElement.normalize()

            val nodes = doc.getElementsByTagName(fieldName)
            if (nodes.length > 0) {
                nodes.item(0).textContent
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readStudentNumberFromXml(fileName: String): String? {
        val file = File(filesDir, fileName)
        if (!file.exists()) return null

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)

        val studentNumberNodes = doc.getElementsByTagName("studentNumber")
        if (studentNumberNodes.length > 0) {
            return studentNumberNodes.item(0).textContent
        }

        return null
    }

    private fun loadXmlAnswers(fileName: String): List<String> {
        val file = File(filesDir, fileName)
        if (!file.exists()) return emptyList()

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("Question")
        return List(nodes.length) { nodes.item(it).textContent }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
