package com.neutrine.knostr

import com.neutrine.knostr.Utils.objectMapper
import com.neutrine.knostr.domain.Event

fun loadFile(fileName: String): String = Application.javaClass.getResource(fileName).readText()
fun createEvent(fileName: String = "/events/event-01.json"): Event = objectMapper.readValue(loadFile(fileName), Event::class.java)
