package me.sedlar.invorg

import com.asprise.imaging.core.Imaging
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
import javafx.stage.Stage
import javafx.stage.WindowEvent
import me.sedlar.invorg.util.NativeBundler
import me.sedlar.invorg.util.OpenCV
import org.opencv.imgcodecs.Imgcodecs
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
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

val scanArgs = """
{
  "twain_cap_setting" : {
    "ICAP_PIXELTYPE" : "TWPT_RGB",
    "ICAP_XRESOLUTION" : "200",
    "ICAP_YRESOLUTION" : "200",
    "ICAP_SUPPORTEDSIZES" : "TWSS_USLETTER"
  },
  "output_settings" : [ {
    "type" : "save",
    "format" : "jpg",
    "save_path" : "${File(NativeBundler.SITE, "tempscan.jpg").absolutePath.replace('\\', '/')}"
  } ]
}
""".trimIndent()

class InvoiceOrganizer : Application() {

    private var changedYearManually = false
    private var changedMonthManually = false
    private var changedCompanyManually = false

    private var invoice: InvoiceMat? = null

    private var imgInvoice: ImageView? = null
    private var btnScan: Button? = null
    private var txtCompany: TextField? = null
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
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupUI(scene: Scene) {
        imgInvoice = scene.lookup("#img-invoice") as ImageView
        btnScan = scene.lookup("#btn-scan") as Button
        txtCompany = scene.lookup("#txt-company") as TextField
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

        txtCompany?.setOnKeyTyped {
            changedCompanyManually = true
        }

        txtYear?.setOnKeyTyped {
            changedYearManually = true
        }

        cmbMonth?.setOnAction {
            changedMonthManually = true
        }

        btnSave?.setOnAction { saveInvoice() }
    }

    private fun setupDateCombo() {
        monthShorthand.forEach {
            cmbMonth?.items?.add(it)
        }
    }

    private fun scanImage(): BufferedImage? {
        val imaging = Imaging(null)
        var image: BufferedImage? = null
        Imaging.getDefaultExecutorServiceForScanning()
            .submit {
                try {
                    val result = imaging.scan(scanArgs, "select", false, true)
                    val originalImage = result.getImage(0)
                    image = BufferedImage(originalImage.width, originalImage.height - 15, BufferedImage.TYPE_INT_RGB)
                    image?.graphics?.drawImage(
                        originalImage.getSubimage(
                            0,
                            0,
                            originalImage.width,
                            originalImage.height - 15
                        ), 0, 0, null
                    )
                } catch (err: Exception) {
                    image = null
                }
            }.get()
        return image
    }

    private fun scanInvoice() {
        Thread {
            scanImage()?.let { jpgImage ->
                val invoiceImage = BufferedImage(
                    jpgImage.getWidth(null),
                    jpgImage.getHeight(null),
                    BufferedImage.TYPE_INT_RGB
                )
                invoiceImage.createGraphics().drawImage(jpgImage, 0, 0, null)

                lblStatus?.isVisible = false
                imgInvoice?.image = SwingFXUtils.toFXImage(invoiceImage, null)

                invoice = InvoiceMat(invoiceImage)

                if (!changedCompanyManually) {
                    Thread {
                        invoice?.parseCompany().let { txtCompany?.text = it }
                    }.start()
                }

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
            val invoiceCompany = invoice.parseCompany()
            val invoiceNumber = invoice.parseNumber()

            if (invoiceNumber != null && txtInvoiceNumber != null && txtYear != null && cmbMonth != null) {
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