package com.socialcoding.events.models

import kotlinx.serialization.Serializable

/**
 * The editable fields of an event, used to create and update.
 *
 * @param title The event title.
 * @param summary A short blurb shown in the list.
 * @param body The full write-up revealed by "Read more".
 * @param startsAt When the next occurrence takes place, in epoch ms.
 * @param location The optional location.
 * @param burrowUrl The optional external Burrow link.
 * @param imageUrl The optional promotional image.
 * @param attendance Whether attendance tracking is enabled.
 * @param recurring Whether the event repeats weekly on [startsAt]'s weekday and
 *   time.
 * @param announce Whether to post the event to the board's Discord announcement
 *   channel at noon on the day it happens.
 */
@Serializable
data class EventRequest(
    val title: String,
    val summary: String,
    val body: String = "",
    val startsAt: Long,
    val location: String? = null,
    val burrowUrl: String? = null,
    val imageUrl: String? = null,
    val attendance: Boolean = false,
    val recurring: Boolean = false,
    val announce: Boolean = false,
)
