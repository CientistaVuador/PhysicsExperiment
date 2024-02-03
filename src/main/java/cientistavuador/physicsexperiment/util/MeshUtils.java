/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.physicsexperiment.util;

import cientistavuador.physicsexperiment.resources.mesh.MeshData;
import cientistavuador.physicsexperiment.util.bakedlighting.LightmapUVs;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.util.DebugShapeFactory;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class MeshUtils {

    public static void generateTangent(float[] vertices, int vertexSize, int xyzOffset, int uvOffset, int outTangentXYZOffset) {
        if (vertices.length % vertexSize != 0) {
            throw new IllegalArgumentException("Wrong size.");
        }
        if (vertices.length % (3 * vertexSize) != 0) {
            throw new IllegalArgumentException("Not a triangulated mesh.");
        }
        for (int v = 0; v < vertices.length; v += (vertexSize * 3)) {
            int v0 = v;
            int v1 = v + vertexSize;
            int v2 = v + (vertexSize * 2);

            float v0x = vertices[v0 + xyzOffset + 0];
            float v0y = vertices[v0 + xyzOffset + 1];
            float v0z = vertices[v0 + xyzOffset + 2];
            float v0u = vertices[v0 + uvOffset + 0];
            float v0v = vertices[v0 + uvOffset + 1];

            float v1x = vertices[v1 + xyzOffset + 0];
            float v1y = vertices[v1 + xyzOffset + 1];
            float v1z = vertices[v1 + xyzOffset + 2];
            float v1u = vertices[v1 + uvOffset + 0];
            float v1v = vertices[v1 + uvOffset + 1];

            float v2x = vertices[v2 + xyzOffset + 0];
            float v2y = vertices[v2 + xyzOffset + 1];
            float v2z = vertices[v2 + xyzOffset + 2];
            float v2u = vertices[v2 + uvOffset + 0];
            float v2v = vertices[v2 + uvOffset + 1];

            float edge1x = v1x - v0x;
            float edge1y = v1y - v0y;
            float edge1z = v1z - v0z;

            float edge2x = v2x - v0x;
            float edge2y = v2y - v0y;
            float edge2z = v2z - v0z;

            float deltaUV1u = v1u - v0u;
            float deltaUV1v = v1v - v0v;

            float deltaUV2u = v2u - v0u;
            float deltaUV2v = v2v - v0v;

            float f = 1f / ((deltaUV1u * deltaUV2v) - (deltaUV2u * deltaUV1v));

            float tangentX = f * ((deltaUV2v * edge1x) - (deltaUV1v * edge2x));
            float tangentY = f * ((deltaUV2v * edge1y) - (deltaUV1v * edge2y));
            float tangentZ = f * ((deltaUV2v * edge1z) - (deltaUV1v * edge2z));

            float length = (float) (1.0 / Math.sqrt((tangentX * tangentX) + (tangentY * tangentY) + (tangentZ * tangentZ)));
            tangentX *= length;
            tangentY *= length;
            tangentZ *= length;

            vertices[v0 + outTangentXYZOffset + 0] = tangentX;
            vertices[v0 + outTangentXYZOffset + 1] = tangentY;
            vertices[v0 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v1 + outTangentXYZOffset + 0] = tangentX;
            vertices[v1 + outTangentXYZOffset + 1] = tangentY;
            vertices[v1 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v2 + outTangentXYZOffset + 0] = tangentX;
            vertices[v2 + outTangentXYZOffset + 1] = tangentY;
            vertices[v2 + outTangentXYZOffset + 2] = tangentZ;
        }
    }

    private static class Vertex {

        final float[] vertices;
        final int vertexSize;
        final int vertexIndex;
        final int vertexCount;

        public Vertex(float[] vertices, int vertexSize, int vertexIndex, int vertexCount) {
            this.vertices = vertices;
            this.vertexSize = vertexSize;
            this.vertexIndex = vertexIndex;
            this.vertexCount = vertexCount;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < this.vertexSize; i++) {
                hash = 27 * hash + Float.floatToRawIntBits(this.vertices[this.vertexIndex + i]);
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Vertex other = (Vertex) obj;
            int indexA = this.vertexIndex;
            int indexB = other.vertexIndex;
            for (int i = 0; i < this.vertexSize; i++) {
                if (Float.floatToRawIntBits(this.vertices[indexA + i]) != Float.floatToRawIntBits(this.vertices[indexB + i])) {
                    return false;
                }
            }
            return true;
        }
    }

    public static Pair<float[], int[]> generateIndices(float[] vertices, int vertexSize) {
        Map<Vertex, Vertex> verticesMap = new HashMap<>();

        float[] verticesIndexed = new float[64];
        int verticesIndexedIndex = 0;

        int[] indices = new int[64];
        int indicesIndex = 0;

        int vertexCount = 0;

        for (int v = 0; v < vertices.length; v += vertexSize) {
            Vertex current = new Vertex(vertices, vertexSize, v, vertexCount);
            Vertex other = verticesMap.get(current);

            if (other != null) {
                if (indicesIndex >= indices.length) {
                    indices = Arrays.copyOf(indices, indices.length * 2);
                }
                indices[indicesIndex] = other.vertexCount;
                indicesIndex++;
                continue;
            }

            verticesMap.put(current, current);

            if ((verticesIndexedIndex + vertexSize) > verticesIndexed.length) {
                verticesIndexed = Arrays.copyOf(verticesIndexed, verticesIndexed.length * 2);
            }
            System.arraycopy(vertices, v, verticesIndexed, verticesIndexedIndex, vertexSize);
            verticesIndexedIndex += vertexSize;

            if (indicesIndex >= indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
            }
            indices[indicesIndex] = vertexCount;
            indicesIndex++;

            vertexCount++;
        }

        return new Pair<>(
                Arrays.copyOf(verticesIndexed, verticesIndexedIndex),
                Arrays.copyOf(indices, indicesIndex)
        );
    }

    public static Pair<float[], int[]> unindex(float[] vertices, int[] indices, int vertexSize) {
        float[] unindexedVertices = new float[indices.length * vertexSize];
        int[] unindexedIndices = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            System.arraycopy(vertices, indices[i] * vertexSize, unindexedVertices, i * vertexSize, vertexSize);
            unindexedIndices[i] = i;
        }
        return new Pair<>(unindexedVertices, unindexedIndices);
    }

    public static LightmapUVs.GeneratorOutput generateLightmapUVs(float[] vertices, int vertexSize, int xyzOffset, float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        return LightmapUVs.generate(vertices, vertexSize, xyzOffset, pixelToWorldRatio, scaleX, scaleY, scaleZ);
    }

    public static void calculateTriangleNormal(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            Vector3f outNormal
    ) {
        outNormal.set(bx, by, bz).sub(ax, ay, az).normalize();

        float baX = outNormal.x();
        float baY = outNormal.y();
        float baZ = outNormal.z();

        outNormal.set(cx, cy, cz).sub(ax, ay, az).normalize();

        float caX = outNormal.x();
        float caY = outNormal.y();
        float caZ = outNormal.z();

        outNormal.set(baX, baY, baZ).cross(caX, caY, caZ).normalize();
    }

    public static void calculateTriangleNormal(float[] vertices, int vertexSize, int xyzOffset, int i0, int i1, int i2, Vector3f outNormal) {
        float ax = vertices[(i0 * vertexSize) + xyzOffset + 0];
        float ay = vertices[(i0 * vertexSize) + xyzOffset + 1];
        float az = vertices[(i0 * vertexSize) + xyzOffset + 2];

        float bx = vertices[(i1 * vertexSize) + xyzOffset + 0];
        float by = vertices[(i1 * vertexSize) + xyzOffset + 1];
        float bz = vertices[(i1 * vertexSize) + xyzOffset + 2];

        float cx = vertices[(i2 * vertexSize) + xyzOffset + 0];
        float cy = vertices[(i2 * vertexSize) + xyzOffset + 1];
        float cz = vertices[(i2 * vertexSize) + xyzOffset + 2];

        calculateTriangleNormal(ax, ay, az, bx, by, bz, cx, cy, cz, outNormal);
    }

    private static float valuesDistance(float[] values) {
        if (values.length == 1) {
            return Math.abs(values[0]);
        }
        float totalSum = 0f;
        for (int i = 0; i < values.length; i++) {
            totalSum += (values[i] * values[i]);
        }
        return (float) Math.sqrt(totalSum);
    }
    
    public static int conservativeMergeByDistance(float[] vertices, int vertexSize, int offset, int size, float distance) {
        int altered = 0;
        boolean[] processed = new boolean[vertices.length / vertexSize];
        
        float[] current = new float[size];
        float[] other = new float[size];
        
        for (int v = 0; v < vertices.length; v += vertexSize) {
            if (processed[v / vertexSize]) {
                continue;
            }
            processed[v / vertexSize] = true;
            
            System.arraycopy(vertices, v + offset, current, 0, size);
            
            for (int vOther = (v + vertexSize); vOther < vertices.length; vOther += vertexSize) {
                if (processed[vOther / vertexSize]) {
                    continue;
                }
                
                System.arraycopy(vertices, vOther + offset, other, 0, size);
                
                for (int i = 0; i < other.length; i++) {
                    other[i] = current[i] - other[i];
                }
                
                float otherDistance = valuesDistance(other);
                
                if (otherDistance == 0f) {
                    processed[vOther / vertexSize] = true;
                    continue;
                }

                if (otherDistance <= distance) {
                    System.arraycopy(current, 0, vertices, vOther + offset, size);
                    processed[vOther / vertexSize] = true;
                    altered++;
                }
            }
        }

        return altered;
    }
    
    public static int conservativeMergeByDistanceXYZ(float[] vertices, int vertexSize, int xyzOffset, float distance) {
        return conservativeMergeByDistance(vertices, vertexSize, xyzOffset, 3, distance);
    }
    
    public static void vertexAO(float[] vertices, int vertexSize, int xyzOffset, int outAoOffset, float aoSize, int aoRays, float rayOffset) {
        VertexAO.vertexAO(vertices, vertexSize, xyzOffset, outAoOffset, aoSize, aoRays, rayOffset);
    }

    public static MeshData createMeshFromCollisionShape(String name, CollisionShape shape) {
        FloatBuffer verts = DebugShapeFactory.getDebugTriangles(shape, DebugShapeFactory.highResolution);
        verts.flip();

        int amountOfVertices = verts.capacity() / 3;
        float[] vertices = new float[amountOfVertices * MeshData.SIZE];
        for (int v = 0; v < amountOfVertices; v++) {
            verts.get(vertices, v * MeshData.SIZE, 3);
        }

        Vector3f normal = new Vector3f();
        for (int v = 0; v < amountOfVertices; v += 3) {
            int i0 = v + 0;
            int i1 = v + 1;
            int i2 = v + 2;

            calculateTriangleNormal(
                    vertices, MeshData.SIZE, MeshData.XYZ_OFFSET,
                    i0, i1, i2,
                    normal
            );

            int v0 = i0 * MeshData.SIZE;
            int v1 = i1 * MeshData.SIZE;
            int v2 = i2 * MeshData.SIZE;

            vertices[v0 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v0 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v0 + MeshData.N_XYZ_OFFSET + 2] = normal.z();

            vertices[v1 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v1 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v1 + MeshData.N_XYZ_OFFSET + 2] = normal.z();

            vertices[v2 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v2 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v2 + MeshData.N_XYZ_OFFSET + 2] = normal.z();
        }
        
        MeshUtils.conservativeMergeByDistanceXYZ(
                vertices, MeshData.SIZE, MeshData.XYZ_OFFSET,
                0.0001f
        );
        
        Pair<float[], int[]> generated = MeshUtils.generateIndices(vertices, MeshData.SIZE);
        
        return new MeshData(name, generated.getA(), generated.getB());
    }

    private MeshUtils() {

    }

}
