package com.example.engine.gl

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class Custom3DMeshPreset(val label: String) {
    UPLOADED_OBJ("Uploaded .OBJ 3D File"),
    CYBER_KNIGHT("Cyber Knight 3D"),
    ROYAL_WARRIOR("Shahi Yoddha 3D"),
    LAVA_DEMON("Jwalamukhi Demon 3D"),
    SHADOW_NINJA("Shadow Assassin 3D"),
    GOLDEN_GUARDIAN("Swarna Rakshak 3D"),
    MECHA_BOT("Mecha Titan 3D")
}

/**
 * Normalized 3D triangle mesh ready for direct OpenGL ES 2.0 rendering inside VoxelEntityManager.
 * Each triangle consists of 3 vertices; each vertex holds (x, y, z, shade) in local character space
 * where feet rest at y = 0.0 and total height is ~1.85 blocks.
 */
data class Parsed3DModelMesh(
    val triangleCount: Int,
    /** Packed as 4 floats per vertex: (lx, ly, lz, faceShade). Length = triangleCount * 3 * 4. */
    val localVertices: FloatArray
)

object CustomModelParser {

    private const val MAX_FILE_BYTES = 1_048_576 // 1 MB safety cap
    private const val MAX_VERTICES = 6_000
    private const val MAX_TRIANGLES = 3_000

    /**
     * Parses a Wavefront `.obj` 3D model stream safely (no external dependencies, strict bounds caps),
     * auto-centers X/Z, places feet at Y = 0, normalizes height to 1.85 blocks, and computes
     * directional face lighting shade for every triangle.
     */
    fun parseObjStream(input: InputStream): Result<Parsed3DModelMesh> {
        return try {
            val positions = ArrayList<Float>(512)
            val triIndices = ArrayList<Int>(1024)

            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var bytesRead = 0
            var line = reader.readLine()
            while (line != null && bytesRead < MAX_FILE_BYTES) {
                bytesRead += line.length + 1
                val trimmed = line.trim()
                if (trimmed.startsWith("v ")) {
                    if (positions.size / 3 < MAX_VERTICES) {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val x = parts[1].toFloatOrNull()
                            val y = parts[2].toFloatOrNull()
                            val z = parts[3].toFloatOrNull()
                            if (x != null && y != null && z != null &&
                                x.isFinite() && y.isFinite() && z.isFinite()
                            ) {
                                positions.add(x)
                                positions.add(y)
                                positions.add(z)
                            }
                        }
                    }
                } else if (trimmed.startsWith("f ")) {
                    val parts = trimmed.split(Regex("\\s+"))
                    val vertCount = positions.size / 3
                    if (parts.size >= 4 && vertCount > 0 && triIndices.size / 3 < MAX_TRIANGLES) {
                        val faceVerts = IntArray(parts.size - 1)
                        var validCount = 0
                        for (i in 1 until parts.size) {
                            val token = parts[i].substringBefore('/')
                            val rawIdx = token.toIntOrNull() ?: continue
                            val idx = if (rawIdx > 0) rawIdx - 1 else vertCount + rawIdx
                            if (idx in 0 until vertCount) {
                                faceVerts[validCount++] = idx
                            }
                        }
                        // Fan triangulation for triangles, quads, and convex n-gons
                        for (k in 1 until validCount - 1) {
                            if (triIndices.size / 3 >= MAX_TRIANGLES) break
                            triIndices.add(faceVerts[0])
                            triIndices.add(faceVerts[k])
                            triIndices.add(faceVerts[k + 1])
                        }
                    }
                }
                line = reader.readLine()
            }

            val vCount = positions.size / 3
            val tCount = triIndices.size / 3
            if (vCount < 3 || tCount < 1) {
                return Result.failure(IllegalArgumentException("3D फ़ाइल में मान्य Wavefront .OBJ वर्टेक्स/फ़ेस नहीं मिले।"))
            }

            // Compute bounding box to center X/Z and normalize height to 1.85 blocks
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            for (i in 0 until vCount) {
                val x = positions[i * 3]
                val y = positions[i * 3 + 1]
                val z = positions[i * 3 + 2]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            }

            val cx = (minX + maxX) * 0.5f
            val cz = (minZ + maxZ) * 0.5f
            val rawHeight = (maxY - minY).coerceAtLeast(0.001f)
            val rawSpan = max(max(maxX - minX, maxZ - minZ), rawHeight).coerceAtLeast(0.001f)
            val normScale = (1.85f / rawSpan).coerceIn(0.001f, 50f)

            val packed = FloatArray(tCount * 3 * 4)
            var out = 0
            // Directional light vector (warm sun from above-front-right)
            val lx = 0.35f; val ly = 0.85f; val lz = 0.40f

            for (t in 0 until tCount) {
                val i0 = triIndices[t * 3]
                val i1 = triIndices[t * 3 + 1]
                val i2 = triIndices[t * 3 + 2]

                val x0 = (positions[i0 * 3] - cx) * normScale
                val y0 = (positions[i0 * 3 + 1] - minY) * normScale
                val z0 = (positions[i0 * 3 + 2] - cz) * normScale

                val x1 = (positions[i1 * 3] - cx) * normScale
                val y1 = (positions[i1 * 3 + 1] - minY) * normScale
                val z1 = (positions[i1 * 3 + 2] - cz) * normScale

                val x2 = (positions[i2 * 3] - cx) * normScale
                val y2 = (positions[i2 * 3 + 1] - minY) * normScale
                val z2 = (positions[i2 * 3 + 2] - cz) * normScale

                // Surface normal for crisp faceted 3D lighting
                val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
                val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
                val dot = abs((nx * lx + ny * ly + nz * lz) / nLen)
                val shade = (0.58f + dot * 0.44f).coerceIn(0.55f, 1.04f)

                packed[out++] = x0; packed[out++] = y0; packed[out++] = z0; packed[out++] = shade
                packed[out++] = x1; packed[out++] = y1; packed[out++] = z1; packed[out++] = shade
                packed[out++] = x2; packed[out++] = y2; packed[out++] = z2; packed[out++] = shade
            }

            Result.success(Parsed3DModelMesh(triangleCount = tCount, localVertices = packed))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("3D मॉडल पढ़ने में समस्या आई: ${e.localizedMessage ?: "अमान्य फ़ाइल"}"))
        }
    }
}
