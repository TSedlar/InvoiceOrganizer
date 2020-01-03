package me.sedlar.invorg

import org.opencv.core.Mat

class InvoiceNumberMat(
    val number: Int,
    val mat: Mat,
    val precision: Double
)