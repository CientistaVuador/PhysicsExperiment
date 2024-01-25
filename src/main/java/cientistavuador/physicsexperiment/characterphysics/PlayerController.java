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
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class PlayerController {

    public static final float HEIGHT = 1.65f;
    public static final float RADIUS = 0.5f / 2f;
    public static final float EYE_HEIGHT = HEIGHT - 0.15f;

    public static final float STEP_HEIGHT = 0.3f;
    public static final float WALK_SPEED = 5f;
    public static final float ACCELERATION = 100f;
    public static final float DECELERATION = 50f;
    public static final float JUMP_SPEED = 8f;
    
    public static final float OUT_OF_GROUND_FACTOR = 0.25f;

    private static final float INVERSE_SQRT_2 = (float) (1.0 / Math.sqrt(2.0));

    public static final float NOCLIP_SPEED = 4.5f;
    public static final float NOCLIP_RUN_SPEED = 13f;

    public static final CompoundCollisionShape PLAYER_COLLISION;

    static {
        CapsuleCollisionShape e = new CapsuleCollisionShape(
                RADIUS,
                HEIGHT - (RADIUS * 2f)
        );
        PLAYER_COLLISION = new CompoundCollisionShape(1);
        PLAYER_COLLISION.addChildShape(e, 0f, HEIGHT / 2f, 0f);
    }

    public static final MeshData PLAYER_COLLISION_MESH = MeshUtils.createMeshFromCollisionShape(
            "playerDebugCollisionMesh",
            PLAYER_COLLISION
    );

    private final CharacterController characterPhysics;

    private final Vector3f eyePosition = new Vector3f();

    private float walkFront = 0f;
    private float walkRight = 0f;

    private float frontX = 0f;
    private float frontZ = 0f;
    private float rightX = 0f;
    private float rightZ = 0f;

    private float currentSpeedX = 0f;
    private float currentSpeedZ = 0f;

    private boolean noclipEnabled = false;
    private boolean crouched = false;
    
    public PlayerController() {
        this.characterPhysics = new CharacterController(HEIGHT, RADIUS, 65f);
        this.characterPhysics.setJumpSpeed(JUMP_SPEED);
    }

    public CharacterController getCharacterPhysics() {
        return characterPhysics;
    }
    
    public Vector3fc getEyePosition() {
        Vector3fc pos = getPosition();

        this.eyePosition
                .set(pos)
                .add(0f, EYE_HEIGHT, 0f);

        return this.eyePosition;
    }

    public Vector3fc getPosition() {
        return this.characterPhysics.getPosition();
    }

    public void setPosition(float x, float y, float z) {
        this.characterPhysics.setPosition(x, y, z);
    }

    public void setPosition(Vector3fc position) {
        setPosition(position.x(), position.y(), position.z());
    }

    public void jump() {
        if (this.noclipEnabled) {
            return;
        }
        this.characterPhysics.jump();
    }

    public boolean onGround() {
        return this.characterPhysics.onGround();
    }

    public boolean isNoclipEnabled() {
        return noclipEnabled;
    }

    public void setNoclipEnabled(boolean noclipEnabled) {
        this.noclipEnabled = noclipEnabled;
        if (noclipEnabled) {
            noclipEnabled();
        } else {
            noclipDisabled();
        }
    }

    private void noclipEnabled() {
        this.characterPhysics.setNoclipEnabled(true);
        this.walkFront = 0f;
        this.walkRight = 0f;
        this.currentSpeedX = 0f;
        this.currentSpeedZ = 0f;
    }

    private void noclipDisabled() {
        this.characterPhysics.setNoclipEnabled(false);
    }

    public boolean isCrouched() {
        return crouched;
    }

    public void setCrouched(boolean crouched, PhysicsSpace space) {
        this.crouched = crouched;
    }

    private void calculateWalk() {
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

        this.walkFront = newWalkFront;
        this.walkRight = newWalkRight;
    }

    private void calculateVectors(Vector3fc frontVector, Vector3fc rightVector) {
        float newFrontX = frontVector.x();
        float newFrontZ = frontVector.z();
        float frontLength = 1f / ((float) Math.sqrt((newFrontX * newFrontX) + (newFrontZ * newFrontZ)));
        newFrontX *= frontLength;
        newFrontZ *= frontLength;
        this.frontX = newFrontX;
        this.frontZ = newFrontZ;

        float newRightX = rightVector.x();
        float newRightZ = rightVector.z();
        float rightLength = 1f / ((float) Math.sqrt((newRightX * newRightX) + (newRightZ * newRightZ)));
        newRightX *= rightLength;
        newRightZ *= rightLength;
        this.rightX = newRightX;
        this.rightZ = newRightZ;
    }

    private void calculateSpeed() {
        float acceleration = ACCELERATION;
        if (!this.characterPhysics.onGround()) {
            acceleration *= OUT_OF_GROUND_FACTOR;
        }
        float factor = (float) (acceleration * Main.TPF);

        float moveX = ((this.frontX * this.walkFront) + (this.rightX * this.walkRight)) * factor;
        float moveZ = ((this.frontZ * this.walkFront) + (this.rightZ * this.walkRight)) * factor;
        
        this.currentSpeedX += moveX;
        this.currentSpeedZ += moveZ;
    }

    private void calculateDeceleration() {
        if (this.currentSpeedX == 0f && this.currentSpeedZ == 0f) {
            return;
        }

        float dirX = this.currentSpeedX;
        float dirZ = this.currentSpeedZ;
        float invlength = -1f / ((float) Math.sqrt((dirX * dirX) + (dirZ * dirZ)));
        dirX *= invlength;
        dirZ *= invlength;
        
        float deceleration = DECELERATION;
        if (!this.characterPhysics.onGround()) {
            deceleration *= OUT_OF_GROUND_FACTOR;
        }
        float factor = (float) (deceleration * Main.TPF);
        
        float speedX = dirX * factor;
        float speedZ = dirZ * factor;
        
        if (Math.signum(this.currentSpeedX + speedX) != Math.signum(this.currentSpeedX)) {
            this.currentSpeedX = 0f;
        } else {
            this.currentSpeedX += speedX;
        }

        if (Math.signum(this.currentSpeedZ + speedZ) != Math.signum(this.currentSpeedZ)) {
            this.currentSpeedZ = 0f;
        } else {
            this.currentSpeedZ += speedZ;
        }
    }

    private void clampSpeed() {
        if (this.currentSpeedX == 0f && this.currentSpeedZ == 0f) {
            return;
        }

        float dirX = this.currentSpeedX;
        float dirZ = this.currentSpeedZ;
        float length = ((float) Math.sqrt((dirX * dirX) + (dirZ * dirZ)));

        float invlength = 1f / length;
        dirX *= invlength;
        dirZ *= invlength;

        if (length > WALK_SPEED) {
            length = WALK_SPEED;
        }

        this.currentSpeedX = dirX * length;
        this.currentSpeedZ = dirZ * length;
    }

    private void noclipMovementUpdate(Vector3fc frontVector, Vector3fc rightVector) {
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

        Vector3fc pos = getPosition();

        float tpfFloat = (float) Main.TPF;
        float posX = pos.x() + ((camRightX * xa + frontVector.x() * za) * tpfFloat);
        float posY = pos.y() + ((camRightY * xa + frontVector.y() * za) * tpfFloat);
        float posZ = pos.z() + ((camRightZ * xa + frontVector.z() * za) * tpfFloat);

        setPosition(posX, posY, posZ);
    }

    public void updateMovement(Vector3fc frontVector, Vector3fc rightVector, float physicsSpaceAccuracy) {
        if (!this.noclipEnabled) {
            calculateWalk();
            calculateVectors(frontVector, rightVector);
            calculateSpeed();
            calculateDeceleration();
            clampSpeed();
            
            this.characterPhysics.setWalkDirection(this.currentSpeedX, this.currentSpeedZ);
        } else {
            noclipMovementUpdate(frontVector, rightVector);
        }
    }

}
