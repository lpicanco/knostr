package com.neutrine.knostr

import com.neutrine.knostr.Utils.objectMapper
import com.neutrine.knostr.domain.Event

fun loadFile(fileName: String): String = Application.javaClass.getResource(fileName).readText()
fun createEvent(): Event = objectMapper.readValue(loadFile("/events/event-00.json"), Event::class.java)
