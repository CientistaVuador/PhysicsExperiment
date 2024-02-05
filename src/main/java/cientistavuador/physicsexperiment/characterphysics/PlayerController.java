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
package cientistavuador.physicsexperiment.characterphysics;

import cientistavuador.physicsexperiment.Main;
import cientistavuador.physicsexperiment.resources.mesh.MeshData;
import cientistavuador.physicsexperiment.util.MeshUtils;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

/**
 *
 * @author Cien
 */
public class PlayerController {

    public static final float HEIGHT = 1.65f;
    public static final float CROUCH_HEIGHT = 1f;
    public static final float RADIUS = 0.5f / 2f;
    public static final float MASS = 65f;
    
    public static final float EYE_OFFSET = -0.15f;

    public static final float WALK_SPEED = 4.5f;
    public static final float CROUCH_SPEED = 1.75f;

    public static final float JUMP_SPEED = 8f;
    public static final float CROUCH_JUMP_SPEED = 5f;

    public static final float OUT_OF_GROUND_SPEED_FACTOR = 0.5f;

    private static final float INVERSE_SQRT_2 = (float) (1.0 / Math.sqrt(2.0));

    public static final float NOCLIP_SPEED = 4.5f;
    public static final float NOCLIP_RUN_SPEED = 13f;

    private final CharacterController characterController;

    private final Vector3f eyePosition = new Vector3f();

    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;

    private boolean crouchPressed = false;

    private MeshData collisionMeshData = null;
    private MeshData crouchCollisionMeshData = null;

    public PlayerController() {
        this.characterController = new CharacterController(RADIUS, HEIGHT, CROUCH_HEIGHT, MASS);
    }

    public CharacterController getCharacterController() {
        return characterController;
    }

    public Vector3fc getEyePosition() {
        Vector3fc pos = this.characterController.getPosition();

        this.eyePosition
                .set(pos)
                .add(0f, this.characterController.getHeight() + EYE_OFFSET, 0f);

        return this.eyePosition;
    }

    public void jump() {
        this.characterController.checkedJump(JUMP_SPEED, CROUCH_JUMP_SPEED);
    }

    public MeshData getCollisionMeshData() {
        if (this.collisionMeshData == null) {
            this.collisionMeshData = MeshUtils.createMeshFromCollisionShape("playerCollisionMesh", this.characterController.getCollisionShape());
        }
        return this.collisionMeshData;
    }

    public MeshData getCrouchCollisionMeshData() {
        if (this.crouchCollisionMeshData == null) {
            this.crouchCollisionMeshData = MeshUtils.createMeshFromCollisionShape("playerCrouchCollisionMesh", this.characterController.getCrouchCollisionShape());
        }
        return this.crouchCollisionMeshData;
    }

    private void checkCrouch() {
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_LEFT_CONTROL) == GLFW_TRUE) {
            if (!this.crouchPressed) {
                this.characterController.setCrouched(true);
            }
            this.crouchPressed = true;
        } else {
            if (this.crouchPressed) {
                this.characterController.setCrouched(false);
            }
            this.crouchPressed = false;
        }
    }

    private void calculateWalkDirection(Vector3fc frontVector, Vector3fc rightVector) {
        float newWalkFront = 0f;
        float newWalkRight = 0f;

        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_W) == GLFW_TRUE) {
            newWalkFront += 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_S) == GLFW_TRUE) {
            newWalkFront -= 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_D) == GLFW_TRUE) {
            newWalkRight += 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_A) == GLFW_TRUE) {
            newWalkRight -= 1f;
        }

        if (newWalkFront != 0f && newWalkRight != 0f) {
            newWalkFront *= INVERSE_SQRT_2;
            newWalkRight *= INVERSE_SQRT_2;
        }

        this.walkDirectionX = (frontVector.x() * newWalkFront) + (rightVector.x() * newWalkRight);
        this.walkDirectionZ = (frontVector.z() * newWalkFront) + (rightVector.z() * newWalkRight);
        if (this.walkDirectionX != 0f && this.walkDirectionZ != 0f) {
            float invlength = 1f / ((float) Math.sqrt((this.walkDirectionX * this.walkDirectionX) + (this.walkDirectionZ * this.walkDirectionZ)));
            this.walkDirectionX *= invlength;
            this.walkDirectionZ *= invlength;
        }
    }

    private void noclipMovement(Vector3fc frontVector, Vector3fc rightVector) {
        int directionX = 0;
        int directionZ = 0;

        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_W) == GLFW_PRESS) {
            directionZ += 1;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_S) == GLFW_PRESS) {
            directionZ += -1;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_A) == GLFW_PRESS) {
            directionX += -1;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_D) == GLFW_PRESS) {
            directionX += 1;
        }

        float diagonal = (Math.abs(directionX) == 1 && Math.abs(directionZ) == 1) ? 0.707106781186f : 1f;
        float currentSpeed = (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) ? NOCLIP_RUN_SPEED : NOCLIP_SPEED;
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) {
            currentSpeed /= 4f;
        }

        //acceleration in X and Z axis
        float xa = currentSpeed * diagonal * directionX;
        float za = currentSpeed * diagonal * directionZ;

        float camRightX = rightVector.x();
        float camRightY = rightVector.y();
        float camRightZ = rightVector.z();

        Vector3fc pos = this.characterController.getPosition();
        
        float tpfFloat = (float) Main.TPF;
        float posX = pos.x() + ((camRightX * xa + frontVector.x() * za) * tpfFloat);
        float posY = pos.y() + ((camRightY * xa + frontVector.y() * za) * tpfFloat);
        float posZ = pos.z() + ((camRightZ * xa + frontVector.z() * za) * tpfFloat);

        this.characterController.setPosition(posX, posY, posZ);
    }

    public void updateMovement(Vector3fc frontVector, Vector3fc rightVector) {
        if (!this.characterController.isNoclipEnabled()) {
            checkCrouch();
            calculateWalkDirection(frontVector, rightVector);

            this.characterController.setWalkDirection(
                    this.walkDirectionX * (this.characterController.isCrouched() ? CROUCH_SPEED : WALK_SPEED),
                    this.walkDirectionZ * (this.characterController.isCrouched() ? CROUCH_SPEED : WALK_SPEED)
            );
        } else {
            this.walkDirectionX = 0f;
            this.walkDirectionZ = 0f;
            this.characterController.setWalkDirection(0f, 0f);
            noclipMovement(frontVector, rightVector);
        }
    }
}
