package com.lilyan_lefevre.puzzleit.feature.locator

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Locates a puzzle piece motif inside a puzzle box image.
 *
 * This implementation uses classic template matching (`matchTemplate`) and tries the 4 right-angle
 * rotations (0/90/180/270). It returns the best candidate rectangles sorted by score descending.
 *
 * Design goals:
 * - Simple and deterministic baseline you can unit/instrumentation test.
 * - No scale/perspective search (assumes images are already warped into the same plane).
 * - Optional edge-based preprocessing to reduce sensitivity to illumination.
 *
 * Limitations:
 * - Template matching is weaker on repeated patterns and may produce false positives.
 * - If the piece is not at the same scale as the box, you need multi-scale search.
 */
class PieceLocator(
    private val config: Config = Config()
) {

    /**
     * Configuration for [PieceLocator].
     *
     * @property maxResults Maximum number of returned candidates.
     * @property minScore Minimum accepted score for `TM_CCOEFF_NORMED`. Typical: 0.6..0.8.
     * @property useEdges If true, runs Canny and matches edges instead of raw grayscale.
     * @property cannyThreshold1 First hysteresis threshold for Canny (low).
     * @property cannyThreshold2 Second hysteresis threshold for Canny (high).
     * @property suppressionFactor Controls how aggressively nearby peaks are suppressed.
     * Values are relative to template size. Typical: 0.4..0.7.
     * @property iouNmsThreshold IoU threshold for final NMS across all candidates/rotations.
     * Typical: 0.3..0.6. Set to 1.0 to effectively disable IoU NMS.
     */
    data class Config(
        val maxResults: Int = 10,
        val minScore: Double = 0.65,
        val useEdges: Boolean = true,
        val cannyThreshold1: Double = 60.0,
        val cannyThreshold2: Double = 180.0,
        val suppressionFactor: Double = 0.5,
        val iouNmsThreshold: Double = 0.45
    ) {
        init {
            require(maxResults > 0) { "maxResults must be > 0" }
            require(minScore in 0.0..1.0) { "minScore must be in [0..1]" }
            require(suppressionFactor in 0.05..2.0) { "suppressionFactor must be in [0.05..2.0]" }
            require(iouNmsThreshold in 0.0..1.0) { "iouNmsThreshold must be in [0..1]" }
        }
    }

    /**
     * A candidate location for the puzzle piece in the puzzle box image.
     *
     * @property rect Candidate rectangle in box coordinates.
     * @property score Match score in [0..1] for `TM_CCOEFF_NORMED` (higher is better).
     * @property rotationDeg Rotation applied to the piece before matching (0/90/180/270).
     */
    data class Candidate(
        val rect: Rect,
        val score: Double,
        val rotationDeg: Int
    )

    /**
     * Finds probable positions of [pieceBgr] inside [boxBgr].
     *
     * @param boxBgr Box image in BGR color space (OpenCV default).
     * @param pieceBgr Puzzle piece image in BGR color space, tightly cropped to motif.
     * @return A list of candidates sorted by descending [Candidate.score].
     */
    fun locate(boxBgr: Mat, pieceBgr: Mat): List<Candidate> {
        require(!boxBgr.empty()) { "boxBgr is empty" }
        require(!pieceBgr.empty()) { "pieceBgr is empty" }

        val boxPre = preprocessForMatching(boxBgr)
        val all = ArrayList<Candidate>(config.maxResults * 4)

        val rotations = intArrayOf(0, 90, 180, 270)
        for (rot in rotations) {
            val pieceRot = rotateRightAngle(pieceBgr, rot)
            val piecePre = preprocessForMatching(pieceRot)

            if (piecePre.cols() <= boxPre.cols() && piecePre.rows() <= boxPre.rows()) {
                val response = Mat()
                Imgproc.matchTemplate(boxPre, piecePre, response, Imgproc.TM_CCOEFF_NORMED)

                val local = extractTopMatchesFromResponse(
                    response = response,
                    templateSize = piecePre.size(),
                    maxToTake = config.maxResults,
                    minScore = config.minScore,
                    rotationDeg = rot
                )
                all.addAll(local)

                response.release()
            }

            pieceRot.release()
            piecePre.release()
        }

        boxPre.release()

        val sorted = all.sortedByDescending { it.score }
        val nms = nonMaximumSuppressionByIou(sorted, config.iouNmsThreshold)

        return nms.take(config.maxResults)
    }

    /**
     * Preprocesses BGR input into a matching space:
     * - BGR -> Gray
     * - Normalize (min-max) into CV_8U
     * - Optional Canny edge map
     */
    private fun preprocessForMatching(bgr: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)

        val norm = Mat()
        Core.normalize(gray, norm, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
        gray.release()

        if (!config.useEdges) return norm

        val edges = Mat()
        Imgproc.Canny(norm, edges, config.cannyThreshold1, config.cannyThreshold2)
        norm.release()

        return edges
    }

    /**
     * Rotates an image by a right angle.
     *
     * @param src Source mat.
     * @param rotationDeg Rotation in degrees: 0/90/180/270.
     * @return A new rotated mat. Caller owns it.
     */
    private fun rotateRightAngle(src: Mat, rotationDeg: Int): Mat {
        val dst = Mat()
        when (rotationDeg) {
            0 -> src.copyTo(dst)
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> throw IllegalArgumentException("rotationDeg must be 0/90/180/270")
        }
        return dst
    }

    /**
     * Extracts the top local maxima from the response map by iterative peak picking + suppression.
     *
     * Suppression is done by painting a filled rectangle around the selected peak with a value
     * below any plausible minimum score (for `TM_CCOEFF_NORMED`, using -1).
     */
    private fun extractTopMatchesFromResponse(
        response: Mat,
        templateSize: Size,
        maxToTake: Int,
        minScore: Double,
        rotationDeg: Int
    ): List<Candidate> {
        val out = ArrayList<Candidate>(maxToTake)
        val resp = response.clone()

        val tplW = templateSize.width.toInt()
        val tplH = templateSize.height.toInt()

        val supW = max(1, (tplW * config.suppressionFactor).toInt())
        val supH = max(1, (tplH * config.suppressionFactor).toInt())

        repeat(maxToTake) {
            val mm = Core.minMaxLoc(resp)
            val bestScore = mm.maxVal
            val bestLoc = mm.maxLoc

            if (bestScore < minScore) return@repeat

            val x = bestLoc.x.toInt()
            val y = bestLoc.y.toInt()
            val rect = Rect(x, y, tplW, tplH)

            out.add(Candidate(rect = rect, score = bestScore, rotationDeg = rotationDeg))

            val left = (x - supW).coerceAtLeast(0)
            val top = (y - supH).coerceAtLeast(0)
            val right = (x + supW).coerceAtMost(resp.cols() - 1)
            val bottom = (y + supH).coerceAtMost(resp.rows() - 1)

            Imgproc.rectangle(
                resp,
                Point(left.toDouble(), top.toDouble()),
                Point(right.toDouble(), bottom.toDouble()),
                Scalar(-1.0),
                Imgproc.FILLED
            )
        }

        resp.release()
        return out
    }

    /**
     * Applies a simple IoU-based Non-Maximum Suppression over already score-sorted candidates.
     *
     * @param sortedCandidates Candidates sorted by score descending.
     * @param iouThreshold If IoU(candidate, kept) > threshold, candidate is discarded.
     */
    private fun nonMaximumSuppressionByIou(
        sortedCandidates: List<Candidate>,
        iouThreshold: Double
    ): List<Candidate> {
        if (sortedCandidates.isEmpty()) return emptyList()
        if (iouThreshold >= 1.0) return sortedCandidates

        val kept = ArrayList<Candidate>(sortedCandidates.size)
        for (c in sortedCandidates) {
            var overlaps = false
            for (k in kept) {
                if (iou(c.rect, k.rect) > iouThreshold) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) kept.add(c)
        }
        return kept
    }

    /**
     * Computes Intersection-over-Union (IoU) for two rectangles.
     *
     * @return IoU in [0..1].
     */
    private fun iou(a: Rect, b: Rect): Double {
        val ax1 = a.x
        val ay1 = a.y
        val ax2 = a.x + a.width
        val ay2 = a.y + a.height

        val bx1 = b.x
        val by1 = b.y
        val bx2 = b.x + b.width
        val by2 = b.y + b.height

        val ix1 = max(ax1, bx1)
        val iy1 = max(ay1, by1)
        val ix2 = min(ax2, bx2)
        val iy2 = min(ay2, by2)

        val iw = max(0, ix2 - ix1)
        val ih = max(0, iy2 - iy1)
        val inter = iw.toDouble() * ih.toDouble()

        val areaA = (a.width.toDouble() * a.height.toDouble())
        val areaB = (b.width.toDouble() * b.height.toDouble())
        val union = areaA + areaB - inter

        return if (union <= 0.0) 0.0 else inter / union
    }
}
