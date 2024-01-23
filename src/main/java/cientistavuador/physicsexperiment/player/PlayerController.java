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
package cientistavuador.physicsexperiment.player;

import cientistavuador.physicsexperiment.Main;
import cientistavuador.physicsexperiment.resources.mesh.MeshData;
import cientistavuador.physicsexperiment.util.MeshUtils;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.objects.PhysicsCharacter;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class PlayerController {

    public static final float HALF_HEIGHT = 1.65f / 2f;
    public static final float HALF_EYE_HEIGHT = HALF_HEIGHT - 0.2f;
    public static final float HALF_SIZE = 0.5f / 2f;
    
    public static final float STEP_HEIGHT = 0.1f;
    public static final float WALK_SPEED = 3f;
    public static final float JUMP_SPEED = 8f;
    
    private static final float INVERSE_SQRT_2 = (float) (1.0 / Math.sqrt(2.0));
    
    public static final CapsuleCollisionShape PLAYER_COLLISION = new CapsuleCollisionShape(
            HALF_SIZE,
            HALF_HEIGHT
    );
    
    public static final MeshData PLAYER_COLLISION_MESH = MeshUtils.createMeshFromCollisionShape(
            "playerDebugCollisionMesh",
            PLAYER_COLLISION
    );
    
    private final PhysicsCharacter characterPhysics = new PhysicsCharacter(
            PLAYER_COLLISION,
            STEP_HEIGHT
    );

    private final Vector3f eyePosition = new Vector3f();
    private final Vector3f position = new Vector3f();
    private final com.jme3.math.Vector3f positionJme = new com.jme3.math.Vector3f();
    
    public PlayerController() {
        this.characterPhysics.setMaxPenetrationDepth(0f);
        this.characterPhysics.setJumpSpeed(JUMP_SPEED);
    }
    
    public PhysicsCharacter getCharacterPhysics() {
        return characterPhysics;
    }
    
    public Vector3fc getEyePosition() {
        Vector3fc pos = getPosition();
        
        this.eyePosition
                .set(pos)
                .add(0f, HALF_EYE_HEIGHT, 0f)
                ;
        
        return this.eyePosition;
    }
    
    public Vector3fc getPosition() {
        this.characterPhysics.getPhysicsLocation(this.positionJme);
        this.position.set(this.positionJme.x, this.positionJme.y, this.positionJme.z);
        return this.position;
    }
    
    public void setPosition(float x, float y, float z) {
        this.positionJme.set(x, y, z);
        this.characterPhysics.setPhysicsLocation(this.positionJme);
    }
    
    public void setPosition(Vector3fc position) {
        setPosition(position.x(), position.y(), position.z());
    }
    
    public void jump() {
        this.characterPhysics.jump();
    }
    
    public boolean onGround() {
        return this.characterPhysics.onGround();
    }
    
    public void updateMovement(Vector3fc frontVector, float physicsSpaceAccuracy) {
        float frontX = frontVector.x();
        float frontZ = frontVector.z();
        float frontLength = 1f / ((float) Math.sqrt((frontX * frontX) + (frontZ * frontZ)));
        frontX *= frontLength * WALK_SPEED * physicsSpaceAccuracy;
        frontZ *= frontLength * WALK_SPEED * physicsSpaceAccuracy;
        
        float rightX = -frontZ;
        float rightZ = frontX;
        
        float walkFront = 0f;
        float walkRight = 0f;
        
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_W) == GLFW_TRUE) {
            walkFront += 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_S) == GLFW_TRUE) {
            walkFront -= 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_D) == GLFW_TRUE) {
            walkRight += 1f;
        }
        if (glfwGetKey(Main.WINDOW_POINTER, GLFW_KEY_A) == GLFW_TRUE) {
            walkRight -= 1f;
        }
        
        if (walkFront != 0f && walkRight != 0f) {
            walkFront *= INVERSE_SQRT_2;
            walkRight *= INVERSE_SQRT_2;
        }
        
        this.characterPhysics.setWalkDirection(new com.jme3.math.Vector3f(
                (frontX * walkFront) + (rightX * walkRight),
                0f,
                (frontZ * walkFront) + (rightZ * walkRight)
        ));
    }
    
}
