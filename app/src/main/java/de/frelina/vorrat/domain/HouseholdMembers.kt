package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.AuthorActivity
import de.frelina.vorrat.data.sync.RemoteMember

/**
 * Mitglied des Haushalts für die Anzeige. Mitgliedschaften sind anonym (nur eine UID pro Handy); Name und letzte
 * Aktivität stammen aus dem Verlauf (höchstens 90 Tage) und können fehlen.
 */
data class HouseholdMember(
    val uid: String,
    val name: String?,
    val joinedAt: Long?,
    val lastActive: Long?,
    val isMe: Boolean,
)

object HouseholdMembers {
    /**
     * Mitgliederliste: dieses Handy zuerst, danach nach Beitritt (älteste zuerst). Der Name ist der neueste im
     * Verlauf verwendete; für dieses Handy gilt [myName] aus den Einstellungen, falls gesetzt.
     */
    fun build(
        members: List<RemoteMember>,
        myUid: String,
        activity: List<AuthorActivity>,
        myName: String? = null,
    ): List<HouseholdMember> {
        val byUid = activity.groupBy { it.authorUid }
        return members.map { m ->
            val events = byUid[m.uid].orEmpty()
            val isMe = m.uid == myUid
            HouseholdMember(
                uid = m.uid,
                name = myName?.trim()?.takeIf { isMe && it.isNotEmpty() }
                    ?: events.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { it.authorName?.trim()?.takeIf(String::isNotEmpty) },
                joinedAt = m.joinedAt,
                lastActive = events.maxOfOrNull { it.createdAt },
                isMe = isMe,
            )
        }.sortedWith(
            compareByDescending<HouseholdMember> { it.isMe }
                .thenBy { it.joinedAt ?: Long.MAX_VALUE }
                .thenBy { it.uid },
        )
    }
}
