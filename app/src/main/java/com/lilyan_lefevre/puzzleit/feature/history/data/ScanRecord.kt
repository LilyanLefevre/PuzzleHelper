package com.lilyan_lefevre.puzzleit.feature.history.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lilyan_lefevre.puzzleit.feature.project.data.Project
import com.lilyan_lefevre.puzzleit.feature.recognition.Match
import java.util.Locale

/** What the person said about a scan: nothing yet, the shown lead was the right place, or none of the leads was. */
object Verdict {
    const val UNKNOWN = "unknown"
    const val CORRECT = "correct"
    const val WRONG = "wrong"
}

/** One lead of a stored scan: a place on the box as fractions of its width and height (0..1), the turn to apply and the confidence. */
data class StoredLead(val x: Float, val y: Float, val rotationDeg: Int, val confidence: Int)

/** A piece scanned on a puzzle, with the leads it got and the person's verdict. Deleted with its puzzle. */
@Entity(
    tableName = "scans",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val createdAt: Long,
    /** The cut-out piece, a small JPEG in the puzzle's folder. */
    val piecePath: String,
    /** The leads in order, see [encodeLeads]. */
    val leads: String,
    val verdict: String = Verdict.UNKNOWN,
    /** Index of the lead the person confirmed, -1 when none. */
    val chosenLead: Int = -1,
) {
    val leadList: List<StoredLead> get() = decodeLeads(leads)

    /** The lead to show in the history: the confirmed one, else the best. */
    val shownLead: StoredLead? get() = leadList.let { it.getOrNull(chosenLead) ?: it.firstOrNull() }
}

/** "x,y,rotation,confidence" per lead, joined by ';' (a locale-proof text, no JSON dependency for four numbers). */
fun encodeLeads(match: Match): String =
    (listOf(match.best) + match.alternatives).joinToString(";") {
        String.format(Locale.US, "%.4f,%.4f,%d,%d", it.col / match.grid.cols, it.row / match.grid.rows, it.rotationDeg, it.confidence)
    }

fun decodeLeads(text: String): List<StoredLead> =
    text.split(';').mapNotNull { part ->
        val f = part.split(',')
        if (f.size != 4) null else StoredLead(f[0].toFloat(), f[1].toFloat(), f[2].toInt(), f[3].toInt())
    }
