import me.sedlar.invorg.InvoiceMat
import me.sedlar.invorg.util.NativeBundler
import me.sedlar.invorg.util.OpenCV
import java.awt.Color
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

fun main() {
    NativeBundler.extractAndUseResourceLibs()
    Files.walk(File("src/test/resources/test_invoices/").toPath()).forEach { path ->
        if (path.toString().endsWith(".jpg")) {
            if (!path.fileName.toString().contains("1273.jpg"))
                return@forEach
            val expectedNumber = path.fileName.toString().replace(".jpg", "")
            val invoiceImg = OpenCV.loadMatableImage("../test_invoices/$expectedNumber.jpg")

            val invoice = InvoiceMat(invoiceImg)

            val img = invoice.image

            val g = img.createGraphics()

            invoice.findNumberBounds()?.let { nBounds ->
                println("!!!!!!!!!!!")
                g.color = Color.green
                // Hide company
                g.fillRect(30, 10, nBounds.x - (nBounds.width * 2.75).toInt(), nBounds.height * 7)
                // Hide serial data
                g.fillRect(
                    nBounds.x - (nBounds.width * 2.4).toInt(),
                    nBounds.y + (nBounds.height * 1.25).toInt(),
                    (nBounds.width * 4.5).toInt(),
                    (nBounds.height * 4.25).toInt()
                )
                // Hide customer/fill-in data
                g.fillRect(30, (nBounds.height * 7.3).toInt(), img.width - 60, img.height - (nBounds.height * 8))
            }

            println("Hiding information for $expectedNumber")
            ImageIO.write(img, "jpg", File("src/test/resources/secure_test_invoices/${expectedNumber}.jpg"))
            println("  Information hidden")

//            exitProcess(0)
        }
    }
}