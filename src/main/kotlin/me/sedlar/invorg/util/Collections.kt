package me.sedlar.invorg.util

import java.awt.Rectangle

fun List<Rectangle>.reduceToUnique(): ArrayList<Rectangle> {
    val uniqueMatches = ArrayList<Rectangle>()
    val blackListed = ArrayList<Int>()
    for (i in this.indices) {
        if (blackListed.contains(i)) {
            continue
        }
        val current = this[i]
        for (j in this.indices) {
            if (j == i || blackListed.contains(j)) {
                continue
            }
            val subject = this[j]
            if (subject.intersects(current)) {
                blackListed.add(j)
            }
        }
    }
    for (i in this.indices) {
        if (!blackListed.contains(i)) {
            uniqueMatches.add(this[i])
        }
    }
    return uniqueMatches
}