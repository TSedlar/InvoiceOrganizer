package me.sedlar.invorg

import me.sedlar.invorg.util.*
import net.sourceforge.tess4j.Tesseract
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.util.*

val tesseract: Tesseract by lazy {
    val tesseract = Tesseract()
    tesseract.setTessVariable("user_defined_dpi", "200")
    tesseract.setDatapath(File(NativeBundler.SITE, "tesseract/").absolutePath)
    tesseract.setLanguage("eng")
    tesseract
}

val modelInvoice: Mat by lazy {
    OpenCV.mat("invoice.png")
}

class InvoiceMat(val image: BufferedImage) {

    val matrix = image.toMat()

    private var number: String = ""

    fun findNumberBounds(): Rectangle? {
        matrix.findFirstTemplate(modelInvoice to 0.515)?.let { invoiceBounds ->
            invoiceBounds.x += modelInvoice.width()
            invoiceBounds.width = matrix.px(10.0)
            return invoiceBounds
        }
        return null
    }

    fun parseNumber(): String? {
        if (number.isNotEmpty()) {
            return number
        }
        var n = parseNumberWithTesseract()
        if (n == null || n.isEmpty()) {
            n = parseNumberWithOpenCV()
        }
        if (n != null) {
            number = n
            return number
        }
        return null
    }

    private fun parseNumberWithTesseract(): String? {
        findNumberBounds()?.let { numberBounds ->
            val nums =
                image.getSubimage(numberBounds.x, numberBounds.y, numberBounds.width, numberBounds.height)
            val numMat = nums.toMat()

            Imgproc.threshold(
                numMat, numMat, 127.0, 255.0,
                Imgproc.THRESH_BINARY and Imgproc.THRESH_OTSU
            )

            val cleanedMat = Mat()
            Imgproc.GaussianBlur(numMat, cleanedMat, Size(5.0, 5.0), 0.0)

            return tesseract.doOCR(cleanedMat.toImage())
                .replace(Regex("[^0-9]"), "")
                .trim()
        }
        return null
    }

    private fun parseNumberWithOpenCV(): String? {
        findNumberBounds()?.let { numberBounds ->
            val numberMat = matrix.submat(numberBounds.toCVRect())

            val numberList = ArrayList<Pair<Int, Rectangle>>()

            for (invoiceNumber in invoiceNumbers) {
                val matches = numberMat
                    .findAllTemplates(invoiceNumber.mat to invoiceNumber.precision)
                    .reduceToUnique()
                if (matches.isNotEmpty()) {
                    matches.forEach {
                        numberList.add(invoiceNumber.number to it)
                    }
                }
            }

            numberList.sortBy { it.second.x }

            number = numberList.joinToString(
                separator = "",
                transform = { it.first.toString() }
            )

            return number
        }
        return null
    }
}