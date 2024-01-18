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
package cientistavuador.physicsexperiment;

import cientistavuador.physicsexperiment.camera.FreeCamera;
import cientistavuador.physicsexperiment.debug.AabRender;
import cientistavuador.physicsexperiment.debug.LineRender;
import cientistavuador.physicsexperiment.geometry.Geometries;
import cientistavuador.physicsexperiment.geometry.Geometry;
import cientistavuador.physicsexperiment.popups.BakePopup;
import cientistavuador.physicsexperiment.resources.mesh.MeshData;
import cientistavuador.physicsexperiment.shader.GeometryProgram;
import cientistavuador.physicsexperiment.text.GLFontRenderer;
import cientistavuador.physicsexperiment.text.GLFontSpecification;
import cientistavuador.physicsexperiment.text.GLFontSpecifications;
import cientistavuador.physicsexperiment.texture.Textures;
import cientistavuador.physicsexperiment.ubo.CameraUBO;
import cientistavuador.physicsexperiment.ubo.UBOBindingPoints;
import cientistavuador.physicsexperiment.util.LightmapFile;
import cientistavuador.physicsexperiment.util.bakedlighting.BakedLighting;
import cientistavuador.physicsexperiment.util.raycast.RayResult;
import cientistavuador.physicsexperiment.util.bakedlighting.SamplingMode;
import cientistavuador.physicsexperiment.util.bakedlighting.Scene;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    private final List<RayResult> rays = new ArrayList<>();
    private final Scene scene = new Scene();

    private final Map<Geometry, LightmapFile.LightmapData> geometryLightmaps = new HashMap<>();

    private final BakedLighting.BakedLightingOutput writeToTexture = new BakedLighting.BakedLightingOutput() {
        private Geometry geometry = null;
        private MeshData.LightmapMesh mesh = null;
        private int lightmapSize = 0;
        private String[] groups = null;
        private int texture = 0;
        private int count = 0;
        private float[][] lightmaps = null;

        @Override
        public void prepare(Geometry geometry, MeshData.LightmapMesh mesh, int lightmapSize, String[] groups) {
            this.geometry = geometry;
            this.mesh = mesh;
            this.lightmapSize = lightmapSize;
            this.groups = groups;
            this.count = groups.length;
            this.lightmaps = new float[groups.length][];

            this.texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            if (Main.isSupported(4, 2)) {
                GL42C.glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length);
            } else {
                glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length, 0, GL_RGBA, GL_FLOAT, 0);
            }

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        }

        @Override
        public void write(float[] lightmap, int groupIndex) {
            this.lightmaps[groupIndex] = lightmap;

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            glTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, groupIndex,
                    this.lightmapSize, this.lightmapSize, 1,
                    GL_RGB, GL_FLOAT, lightmap);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            this.count--;
            if (this.count == 0) {
                this.geometry.setLightmapTextureHint(this.texture);
                this.geometry.setLightmapMesh(this.mesh);

                LightmapFile.Lightmap[] lightmaps = new LightmapFile.Lightmap[this.groups.length];
                for (int i = 0; i < lightmaps.length; i++) {
                    lightmaps[i] = new LightmapFile.Lightmap(this.groups[groupIndex], this.lightmaps[groupIndex]);
                }

                Game.this.geometryLightmaps.put(this.geometry, new LightmapFile.LightmapData(
                        this.mesh.getPixelToWorldRatio(),
                        this.mesh.getScaleX(),
                        this.mesh.getScaleY(),
                        this.mesh.getScaleZ(),
                        this.lightmapSize,
                        lightmaps
                ));
            }
        }
    };

    private BakedLighting.Status status = BakedLighting.dummyStatus();
    private float interiorIntensity = 1f;
    private float sunIntensity = 1f;
    private boolean interiorEnabled = true;
    private boolean sunEnabled = true;

    private boolean bakeWindowOpen = false;
    private final AtomicBoolean saveLightmapProcessing = new AtomicBoolean(false);

    private Game() {

    }

    public void loadLightmap(Geometry geometry, String lightmap) {
        LightmapFile.LightmapData data;
        try {
            try (InputStream stream = Geometries.class.getResourceAsStream(lightmap)) {
                data = LightmapFile.decode(stream);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            return;
        }
        
        MeshData.LightmapMesh mesh = geometry.getMesh().scheduleLightmapMesh(
                data.pixelToWorldRatio(),
                data.scaleX(), data.scaleY(), data.scaleZ()
        );
        String[] groups = new String[data.lightmaps().length];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = data.lightmaps()[i].groupName();
        }
        
        this.writeToTexture.prepare(geometry, mesh, data.lightmapSize(), groups);
        
        for (int i = 0; i < data.lightmaps().length; i++) {
            this.writeToTexture.write(data.lightmaps()[i].data(), i);
        }
        
        geometry.setLightmapMesh(mesh);
    }
    
    public void start() {
        camera.setPosition(1f, 3f, -5f);
        camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.setModel(new Matrix4f());
        program.setColor(1f, 1f, 1f, 1f);
        program.setSunDirection(new Vector3f(-1f, -1f, 0f).normalize());
        program.setSunDiffuse(1f, 1f, 1f);
        program.setSunAmbient(0.2f, 0.2f, 0.2f);
        program.setTextureUnit(0);
        program.setLightingEnabled(true);
        glUseProgram(0);

        for (int i = 0; i < 4; i++) {
            this.scene.getGeometries().add(new Geometry(Geometries.GARAGE[i]));
        }
        loadLightmap(this.scene.getGeometries().get(0), "concrete.lightmap");
        loadLightmap(this.scene.getGeometries().get(1), "grass.lightmap");
        loadLightmap(this.scene.getGeometries().get(2), "bricks.lightmap");
        loadLightmap(this.scene.getGeometries().get(3), "red.lightmap");
        this.geometryLightmaps.clear();

        this.scene.setIndirectLightingEnabled(true);
        this.scene.setDirectLightingEnabled(true);
        this.scene.setShadowsEnabled(true);

        this.scene.setIndirectLightingBlurArea(4f);
        this.scene.setShadowBlurArea(1.2f);

        this.scene.setSamplingMode(SamplingMode.SAMPLE_16);

        this.scene.setFastModeEnabled(false);

        Scene.DirectionalLight sun = new Scene.DirectionalLight();
        sun.setGroupName("sun");
        this.scene.getLights().add(sun);
    }

    public void loop() {
        if (!this.status.isDone()) {
            try {
                Thread.sleep(16);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        for (RayResult r : this.rays) {
            LineRender.queueRender(r.getOrigin(), r.getHitPosition());
        }

        if (this.status.hasError()) {
            try {
                this.status.throwException();
            } catch (ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        float speed = 1f;

        if (this.interiorEnabled) {
            this.interiorIntensity += Main.TPF * speed;
        } else {
            this.interiorIntensity -= Main.TPF * speed;
        }

        if (this.sunEnabled) {
            this.sunIntensity += Main.TPF * speed;
        } else {
            this.sunIntensity -= Main.TPF * speed;
        }

        this.interiorIntensity = Math.min(Math.max(this.interiorIntensity, 0f), 1f);
        this.sunIntensity = Math.min(Math.max(this.sunIntensity, 0f), 1f);

        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(0, this.interiorIntensity);
        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(1, this.sunIntensity);

        camera.updateMovement();
        camera.updateUBO();

        Matrix4f cameraProjectionView = new Matrix4f(this.camera.getProjectionView());

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.updateLightsUniforms();
        program.setProjectionView(cameraProjectionView);
        program.setTextureUnit(0);
        program.setLightmapTextureUnit(1);
        program.setLightingEnabled(false);
        program.setColor(1f, 1f, 1f, 1f);
        for (Geometry geo : this.scene.getGeometries()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geo.getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, geo.getLightmapTextureHint());
            program.setModel(geo.getModel());

            MeshData mesh = geo.getMesh();
            MeshData.LightmapMesh lightmap = geo.getLightmapMesh();

            if (lightmap == null || !lightmap.isDone()) {
                glBindVertexArray(mesh.getVAO());
            } else {
                glBindVertexArray(lightmap.getVAO());
            }
            mesh.render();
            glBindVertexArray(0);
        }
        for (Scene.Light light : this.scene.getLights()) {
            if (light instanceof Scene.PointLight p) {
                float r = p.getDiffuse().x();
                float g = p.getDiffuse().y();
                float b = p.getDiffuse().z();
                float max = Math.max(r, Math.max(g, b));
                if (max > 1f) {
                    float invmax = 1f / max;
                    r *= invmax;
                    g *= invmax;
                    b *= invmax;
                }
                program.setColor(r, g, b, 1f);
                Matrix4f model = new Matrix4f();
                model.translate(p.getPosition()).scale(p.getLightSize());
                program.setModel(model);

                MeshData sphere = Geometries.SPHERE;

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, sphere.getTextureHint());
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);

                glBindVertexArray(Geometries.SPHERE.getVAO());
                sphere.render();
                glBindVertexArray(0);
            }
        }
        glUseProgram(0);

        AabRender.renderQueue(camera);
        LineRender.renderQueue(camera);

        if (!this.status.isDone()) {
            String[] text = new String[]{
                new StringBuilder()
                .append(this.status.getASCIIProgressBar()).append('\n')
                .append(this.status.getCurrentStatus()).append('\n')
                .append(this.status.getRaysPerSecondFormatted()).append('\n')
                .append("Estimated Time: ").append(this.status.getEstimatedTimeFormatted()).append("\n")
                .toString()
            };
            GLFontRenderer.render(-0.895f, 0.795f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK}, text);
            GLFontRenderer.render(-0.90f, 0.80f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE}, text);
        }

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(camera.getPosition().x()) + ",y:" + (int) Math.floor(camera.getPosition().y()) + ",z:" + (int) Math.ceil(camera.getPosition().z()) + ")";
    }

    public void bakePopupCallback(BakePopup popup) {
        if (!this.status.isDone()) {
            return;
        }

        for (Geometry geo : this.scene.getGeometries()) {
            if (geo.getLightmapTextureHint() != Textures.EMPTY_LIGHTMAP) {
                glDeleteTextures(geo.getLightmapTextureHint());
                geo.setLightmapTextureHint(Textures.EMPTY_LIGHTMAP);
            }
        }

        try {
            //config
            popup.getPixelToWorldRatio().commitEdit();
            float pixelToWorldRatio = ((Number) popup.getPixelToWorldRatio().getValue()).floatValue();
            SamplingMode samplingMode = (SamplingMode) popup.getSamplingMode().getSelectedItem();
            popup.getRayOffset().commitEdit();
            float rayOffset = ((Number) popup.getRayOffset().getValue()).floatValue();
            boolean fillEmptyValues = popup.getFillEmptyValues().isSelected();
            boolean fastMode = popup.getFastMode().isSelected();

            this.scene.setSamplingMode(samplingMode);
            this.scene.setRayOffset(rayOffset);
            this.scene.setFillDisabledValuesWithLightColors(fillEmptyValues);
            this.scene.setFastModeEnabled(fastMode);

            //direct
            boolean directEnabled = popup.getDirectLighting().isSelected();
            popup.getDirectAttenuation().commitEdit();
            float attenuation = ((Number) popup.getDirectAttenuation().getValue()).floatValue();

            this.scene.setDirectLightingEnabled(directEnabled);
            this.scene.setDirectLightingAttenuation(attenuation);

            //shadows
            boolean shadowsEnabled = popup.getShadows().isSelected();
            popup.getShadowRays().commitEdit();
            int shadowRays = ((Number) popup.getShadowRays().getValue()).intValue();
            popup.getShadowBlur().commitEdit();
            float shadowBlur = ((Number) popup.getShadowBlur().getValue()).floatValue();

            this.scene.setShadowsEnabled(shadowsEnabled);
            this.scene.setShadowRaysPerSample(shadowRays);
            this.scene.setShadowBlurArea(shadowBlur);

            //indirect
            boolean indirectEnabled = popup.getIndirectLighting().isSelected();
            popup.getIndirectRays().commitEdit();
            int indirectRays = ((Number) popup.getIndirectRays().getValue()).intValue();
            popup.getIndirectBounces().commitEdit();
            int bounces = ((Number) popup.getIndirectBounces().getValue()).intValue();
            popup.getIndirectBlur().commitEdit();
            float indirectBlur = ((Number) popup.getIndirectBlur().getValue()).floatValue();
            popup.getIndirectReflectionFactor().commitEdit();
            float reflectionFactor = ((Number) popup.getIndirectReflectionFactor().getValue()).floatValue();

            this.scene.setIndirectLightingEnabled(indirectEnabled);
            this.scene.setIndirectRaysPerSample(indirectRays);
            this.scene.setIndirectBounces(bounces);
            this.scene.setIndirectLightingBlurArea(indirectBlur);
            this.scene.setIndirectLightReflectionFactor(reflectionFactor);

            this.status = BakedLighting.bake(this.writeToTexture, this.scene, pixelToWorldRatio);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void mouseCursorMoved(double x, double y) {
        camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            if (!this.bakeWindowOpen) {
                this.bakeWindowOpen = true;
                BakePopup.show((t) -> {
                    Main.MAIN_TASKS.add(() -> {
                        Game.this.bakePopupCallback(t);
                    });
                }, (t) -> {
                    this.bakeWindowOpen = false;
                });
                if (this.camera.isCaptureMouse()) {
                    this.camera.pressEscape();
                }
            }
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            if (!this.geometryLightmaps.isEmpty() && !this.saveLightmapProcessing.get()) {
                this.saveLightmapProcessing.set(true);
                final List<LightmapFile.LightmapData> finalList = new ArrayList<>();
                final List<Geometry> finalGeometryList = new ArrayList<>();
                for (Map.Entry<Geometry, LightmapFile.LightmapData> lightmap : this.geometryLightmaps.entrySet()) {
                    finalList.add(lightmap.getValue());
                    finalGeometryList.add(lightmap.getKey());
                }
                new Thread(() -> {
                    try {
                        JFrame dummyFrame = new JFrame("dummy frame");
                        dummyFrame.setLocationRelativeTo(null);
                        dummyFrame.setVisible(true);
                        dummyFrame.toFront();
                        dummyFrame.setVisible(false);

                        JFileChooser chooser = new JFileChooser();
                        chooser.setCurrentDirectory(null);
                        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        chooser.setAcceptAllFileFilterUsed(false);
                        int option = chooser.showOpenDialog(dummyFrame);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            File directory = chooser.getSelectedFile();
                            if (!directory.exists()) {
                                directory.mkdirs();
                            }
                            for (int i = 0; i < finalList.size(); i++) {
                                LightmapFile.LightmapData lightmap = finalList.get(i);
                                Geometry geometry = finalGeometryList.get(i);
                                
                                File output = new File(directory, i+"_"+geometry.getMesh().getName()+".lightmap");
                                
                                try {
                                    try (FileOutputStream outputStream = new FileOutputStream(output)) {
                                        LightmapFile.encode(lightmap, 0.001f, outputStream);
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace(System.out);
                                }
                            }
                        }
                    } finally {
                        this.saveLightmapProcessing.set(false);
                    }
                }).start();
            }
        }
        if (key == GLFW_KEY_I && action == GLFW_PRESS) {
            this.interiorEnabled = !this.interiorEnabled;
        }
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            this.sunEnabled = !this.sunEnabled;
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
