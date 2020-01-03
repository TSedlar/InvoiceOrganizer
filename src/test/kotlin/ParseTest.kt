import me.sedlar.invorg.InvoiceMat
import me.sedlar.invorg.util.NativeBundler
import me.sedlar.invorg.util.OpenCV
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ParseTest {

    @Before
    fun initLibs() {
        NativeBundler.extractAndUseResourceLibs()
    }

    @Test
    fun checkInvoiceNumberParsing() {
        println("checkInvoiceNumberParsing:")
        Files.walk(File("src/test/resources/secure_test_invoices/").toPath()).forEach { path ->
            if (path.toString().endsWith(".jpg")) {
                val expectedNumber = path.fileName.toString().replace(".jpg", "")
                val invoiceImg = OpenCV.loadMatableImage("../secure_test_invoices/$expectedNumber.jpg")

                val invoice = InvoiceMat(invoiceImg)

                val parsedNumber = invoice.parseNumber()

                assert(expectedNumber == parsedNumber) {
                    "$expectedNumber == $parsedNumber"
                }

                println("[Passed] $expectedNumber == $parsedNumber")
            }
        }
    }
}