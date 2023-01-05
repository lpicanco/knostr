package com.neutrine.knostr

fun loadFile(fileName: String): String = Application.javaClass.getResource(fileName).readText()
