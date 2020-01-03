package me.sedlar.invorg

import javafx.application.Application
import javafx.application.Platform
import javafx.application.Platform.runLater
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.WindowEvent
import me.sedlar.invorg.util.NativeBundler
import me.sedlar.invorg.util.OpenCV
import me.sedlar.invorg.util.StreamGobbler
import org.opencv.imgcodecs.Imgcodecs
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.system.exitProcess

val invoiceNumbers: List<InvoiceNumberMat> by lazy {
    listOf(
        "0" to 0.75,
        "1" to 0.75,
        "bad_ink/1" to 0.75,
        "2" to 0.75,
        "3" to 0.845,
        "4" to 0.75,
        "5" to 0.75,
        "6" to 0.75,
        "7" to 0.75,
        "bad_ink/7" to 0.75,
        "8" to 0.845,
        "bad_ink/8" to 0.845,
        "9" to 0.75,
        "bad_ink/9" to 0.75
    ).map { pair ->
        InvoiceNumberMat(
            Scanner(pair.first).useDelimiter("[^\\d]+").nextInt(),
            OpenCV.mat("${pair.first}.png", Imgcodecs.IMREAD_GRAYSCALE),
            pair.second
        )
    }
}

val monthShorthand = arrayOf(
    "Jan", "Feb", "March", "April", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"
)

class InvoiceOrganizer : Application() {

    private var changedYearManually = false
    private var changedMonthManually = false

    private var invoice: InvoiceMat? = null

    private var lblLoad: Label? = null
    private var imgInvoice: ImageView? = null
    private var btnScan: Button? = null
    private var cmbCompany: ComboBox<String>? = null
    private var txtInvoiceNumber: TextField? = null
    private var txtYear: TextField? = null
    private var cmbMonth: ComboBox<String>? = null
    private var lblStatus: Label? = null
    private var btnSave: Button? = null

    override fun start(stage: Stage) {
        val root = FXMLLoader.load<Parent>(javaClass.getResource("/jfx/main.fxml"))

        val scene = Scene(root)

        scene.stylesheets.add(this.javaClass.getResource("/jfx/main.css").toExternalForm())

        stage.icons.add(Image(this.javaClass.getResourceAsStream("/icon.png")))

        stage.title = "Invoice Organizer"
        stage.scene = scene
        stage.show()

        stage.onCloseRequest = EventHandler<WindowEvent> {
            Platform.exit()
            exitProcess(0)
        }

        runLater {
            setupUI(scene)
            setupProperties()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupUI(scene: Scene) {
        lblLoad = scene.lookup("#lbl-load") as Label
        imgInvoice = scene.lookup("#img-invoice") as ImageView
        btnScan = scene.lookup("#btn-scan") as Button
        cmbCompany = scene.lookup("#cmb-company") as ComboBox<String>
        txtInvoiceNumber = scene.lookup("#txt-invoice-number") as TextField
        txtYear = scene.lookup("#txt-year") as TextField
        cmbMonth = scene.lookup("#cmb-month") as ComboBox<String>
        lblStatus = scene.lookup("#lbl-status") as Label
        btnSave = scene.lookup("#btn-save") as Button

        setupDateCombo()

        imgInvoice?.setOnMouseClicked {
            Desktop.getDesktop().browse(File(NativeBundler.SITE, "tempscan.jpg").toURI())
        }

        btnScan?.setOnAction { scanInvoice() }

        txtYear?.setOnKeyTyped {
            changedYearManually = true
        }

        cmbMonth?.setOnAction {
            changedMonthManually = true
        }

        btnSave?.setOnAction { saveInvoice() }
    }

    private fun setupProperties() {
        val companyFile = File("./companies.txt")
        if (companyFile.exists()) {
            val companyTxt = String(Files.readAllBytes(companyFile.toPath()))
            val companies = companyTxt.lines()
            cmbCompany?.items?.clear()
            companies.forEach { company ->
                cmbCompany?.items?.add(company)
            }
            cmbCompany?.selectionModel?.selectFirst()
        }
    }

    private fun setupDateCombo() {
        monthShorthand.forEach {
            cmbMonth?.items?.add(it)
        }
    }

    private fun scanImage(): BufferedImage? {
        val dir = File(NativeBundler.SITE, "lib/")
        val targetFile = File(NativeBundler.SITE, "tempscan.jpg")
        val command =
            "wia-cmd-scanner.exe /w 0 /h 0 /dpi 200 /color RGB /format JPG /output \"${targetFile.absolutePath}\""

        if (targetFile.exists()) {
            targetFile.delete()
        }
        try {

            val builder = ProcessBuilder()

            builder.command("cmd.exe", "/c", command)

            builder.directory(dir)

            val process = builder.start()
            val streamGobbler = StreamGobbler(process.inputStream) { s ->
                println(s)
            }

            Executors.newSingleThreadExecutor().submit(streamGobbler)

            process.waitFor()

            return ImageIO.read(File(NativeBundler.SITE, "tempscan.jpg"))
        } catch (err: Exception) {
            err.printStackTrace()
            return null
        }
    }

    private fun scanInvoice() {
        Thread {
            var scanned = false
            runLater {
                imgInvoice?.image = null
                lblLoad?.textFill = Color.WHITE
                var dots = ""
                Thread {
                    while (!scanned) {
                        runLater {
                            lblLoad?.text = "Scanning${dots}"
                        }
                        if (dots.length >= 3) {
                            dots = ""
                        } else {
                            dots += "."
                        }
                        Thread.sleep(500L)
                    }
                }.start()
            }
            val scannedImg: BufferedImage? = scanImage()
            scanned = true
            if (scannedImg == null) {
                lblLoad?.textFill = Color.RED
                lblLoad?.text = "Scan failed!"
            }
            scannedImg?.let { jpgImage ->
                runLater {
                    lblLoad?.text = ""
                }

                val invoiceImage = BufferedImage(
                    jpgImage.getWidth(null),
                    jpgImage.getHeight(null),
                    BufferedImage.TYPE_INT_RGB
                )
                invoiceImage.createGraphics().drawImage(jpgImage, 0, 0, null)

                lblStatus?.isVisible = false
                imgInvoice?.image = SwingFXUtils.toFXImage(invoiceImage, null)

                invoice = InvoiceMat(invoiceImage)

                Thread {
                    invoice?.parseNumber()?.let { txtInvoiceNumber?.text = it }
                }.start()

                val calendar = Calendar.getInstance()
                calendar.time = Date()

                if (!changedYearManually) {
                    txtYear?.text = calendar.get(Calendar.YEAR).toString()
                }

                if (!changedMonthManually) {
                    runLater {
                        cmbMonth?.selectionModel?.select(monthShorthand[calendar.get(Calendar.MONTH)])
                    }
                }
            }
        }.start()
    }

    private fun saveInvoice() {
        invoice?.let { invoice ->
            val invoiceNumber = invoice.parseNumber()

            if (invoiceNumber != null && txtInvoiceNumber != null && txtYear != null && cmbCompany != null && cmbMonth != null) {
                val invoiceCompany = cmbCompany!!.selectionModel.selectedItem
                val monthName = cmbMonth!!.selectionModel.selectedItem
                var monthNumber = (monthShorthand.indexOf(monthName) + 1).toString()

                if (monthNumber.length == 1) {
                    monthNumber = "0$monthNumber"
                }

                val invoicePath = File(
                    System.getProperty("user.home") +
                            File.separator + "Documents" +
                            File.separator + "Scanned Documents" +
                            File.separator + invoiceCompany.toUpperCase() +
                            File.separator + "Invoices ${txtYear!!.text}" +
                            File.separator + "$invoiceCompany-$monthNumber $monthName ${txtYear!!.text} invoices" +
                            File.separator + "$invoiceNumber.jpg"
                )

                invoicePath.parentFile.mkdirs()

                lblStatus?.text = "Saving..."
                lblStatus?.isVisible = true

                Thread {
                    ImageIO.write(invoice.image, "jpg", invoicePath)
                    runLater {
                        lblStatus?.text = "Saved!"
                    }
                }.start()
            }
        }
    }
}

fun main() {
    object : Thread() {
        override fun run() {
            NativeBundler.extractAndUseResourceLibs()
            Application.launch(InvoiceOrganizer::class.java)
        }
    }.start()
}