package me.sedlar.invorg.util

import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.PixelGrabber
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object OpenCV {

    fun loadMatableImage(imgPath: String): BufferedImage {
        val img = ImageIO.read(javaClass.getResource("/models/$imgPath"))

        val rgbImage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB)
        rgbImage.createGraphics().drawImage(img, 0, 0, null)

        return rgbImage
    }

    fun mat(imgPath: String, format: Int): Mat {
        val rgbImage = loadMatableImage(imgPath)

        val mat = rgbImage.toMat()

        if (format != Imgcodecs.IMREAD_GRAYSCALE) {
            println("converting rgba->rgb")
            val reducedColor = Mat()
            Imgproc.cvtColor(mat, reducedColor, Imgproc.COLOR_RGBA2RGB)
            return reducedColor
        }

        return mat
    }

    fun mat(imgPath: String) = mat(imgPath, Imgcodecs.IMREAD_UNCHANGED)

    fun modelRange(imgDir: String, range: IntRange, weight: Double, format: Int): List<Pair<Mat, Double>> {
        val list: MutableList<Pair<Mat, Double>> = ArrayList()
        for (i in range) {
            list.add(mat("$imgDir/$i.png", format) to weight)
        }
        return list
    }

    fun cannyModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        val list: MutableList<Pair<Mat, Double>> = ArrayList()
        for (i in range) {
            list.add(mat("$imgDir/$i.png", Imgcodecs.IMREAD_GRAYSCALE).canny() to weight)
        }
        return list
    }

    fun bwModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        return modelRange(imgDir, range, weight, Imgcodecs.IMREAD_GRAYSCALE)
    }

    fun normModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        return modelRange(imgDir, range, weight, Imgcodecs.IMREAD_UNCHANGED)
    }
}

fun Mat.canny(threshold: Double, ratio: Double): Mat {
    val edges = Mat()
    Imgproc.Canny(this, edges, threshold, threshold * ratio)
    return edges
}

fun Mat.canny(): Mat = canny(100.0, 3.5)

fun Mat.toImage(): BufferedImage {
    val mob = MatOfByte()
    Imgcodecs.imencode(".png", this, mob)

    val img = ImageIO.read(ByteArrayInputStream(mob.toArray()))

    val rgbImage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB)
    rgbImage.createGraphics().drawImage(img, 0, 0, null)

    return rgbImage
}

fun Mat.pixels(): IntArray {
    PixelGrabber(toImage(), 0, 0, width(), height(), true).let {
        it.grabPixels()
        return it.pixels as IntArray
    }
}

fun BufferedImage.toMat(): Mat {
    val out: Mat
    val data: ByteArray
    var r: Int
    var g: Int
    var b: Int

    if (this.type == BufferedImage.TYPE_INT_RGB) {
        out = Mat(this.height, this.width, CvType.CV_8UC3)
        data = ByteArray(this.width * this.height * out.elemSize().toInt())
        val dataBuff = this.getRGB(0, 0, this.width, this.height, null, 0, this.width)
        for (i in dataBuff.indices) {
            data[i * 3] = (dataBuff[i] shr 0 and 0xFF).toByte()
            data[i * 3 + 1] = (dataBuff[i] shr 8 and 0xFF).toByte()
            data[i * 3 + 2] = (dataBuff[i] shr 16 and 0xFF).toByte()
        }
    } else {
        out = Mat(this.height, this.width, CvType.CV_8UC1)
        data = ByteArray(this.width * this.height * out.elemSize().toInt())
        val dataBuff = this.getRGB(0, 0, this.width, this.height, null, 0, this.width)
        for (i in dataBuff.indices) {
            r = (dataBuff[i] shr 0 and 0xFF).toByte().toInt()
            g = (dataBuff[i] shr 8 and 0xFF).toByte().toInt()
            b = (dataBuff[i] shr 16 and 0xFF).toByte().toInt()
            data[i] = (0.21 * r + 0.71 * g + 0.07 * b).toByte()
        }
    }
    out.put(0, 0, data)
    return out
}

private fun Mat.findTemplates(breakFirst: Boolean, vararg templates: Pair<Mat, Double>): List<Rectangle> {
    val results: MutableList<Rectangle> = ArrayList()

    for (templatePair in templates) {
        val template = templatePair.first
        val threshold = templatePair.second

        if (template.width() > this.width() || template.height() > this.height()) {
            continue
        }

        val result = Mat()

        val method = Imgproc.TM_CCOEFF_NORMED

        Imgproc.matchTemplate(this, template, result, method)
        Imgproc.threshold(result, result, 0.1, 1.0, Imgproc.THRESH_TOZERO)

        while (true) {
            val mml = Core.minMaxLoc(result)
            val pos = mml.maxLoc
            if (mml.maxVal >= threshold) {
                Imgproc.rectangle(
                    this, pos, Point(pos.x + template.cols(), pos.y + template.rows()),
                    Scalar(0.0, 255.0, 0.0), 1
                )
                Imgproc.rectangle(
                    result, pos, Point(pos.x + template.cols(), pos.y + template.rows()),
                    Scalar(0.0, 255.0, 0.0), -1
                )
                results.add(Rectangle(pos.x.toInt(), pos.y.toInt(), template.width(), template.height()))
                if (breakFirst) {
                    return results
                }
            } else {
                break
            }
        }
    }

    return results
}

fun Mat.findAllTemplates(vararg templates: Pair<Mat, Double>): List<Rectangle> {
    return this.findTemplates(false, *templates)
}

fun Mat.findFirstTemplate(vararg templates: Pair<Mat, Double>): Rectangle? {
    val result = findTemplates(true, *templates)
    return if (result.isNotEmpty()) result[0] else null
}

fun Mat.px(percent: Double): Int {
    return (this.width() * (percent / 100.0)).toInt()
}

fun Mat.py(percent: Double): Int {
    return (this.height() * (percent / 100.0)).toInt()
}

fun Mat.prect(px: Double, py: Double, pw: Double, ph: Double): Rectangle {
    return Rectangle(this.px(px), this.py(py), this.px(pw), this.py(ph))
}

fun Rectangle.toCVRect(): Rect {
    return Rect(this.x, this.y, this.width, this.height)
}

fun Rect.toRectangle(): Rectangle {
    return Rectangle(this.x, this.y, this.width, this.height)
}

fun Mat.contours(translate: java.awt.Point? = null): ArrayList<Rectangle> {
    val edges = this.canny()
    val contours = ArrayList<MatOfPoint>()
    val rects = ArrayList<Rectangle>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    for (i in 0 until contours.size) {
        val rect = Imgproc.boundingRect(contours[i]).toRectangle()
        translate?.let { rect.translate(it.x, it.y) }
        rects.add(rect)
    }
    rects.sortBy { it.x }
    return rects
}

fun Mat.firstContour(): Mat? {
    val contours = contours()
    return if (contours.isNotEmpty()) this.submat(contours.first().toCVRect()) else null
}