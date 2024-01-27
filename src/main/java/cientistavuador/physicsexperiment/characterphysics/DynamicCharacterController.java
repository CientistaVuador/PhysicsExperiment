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

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class DynamicCharacterController implements PhysicsTickListener {

    //physics space
    private PhysicsSpace space = null;

    //character info
    private final float totalHeight;
    private final float crouchTotalHeight;
    private final float radius;

    //character collision
    private final CollisionShape collisionShape;
    private final CollisionShape crouchCollisionShape;

    private final PhysicsRigidBody rigidBody;

    //character state
    private boolean noclipEnabled = false;
    private boolean noclipStateChanged = false;

    private boolean crouched = false;
    private boolean crouchStateChanged = false;

    private boolean onGround = false;

    //character movement state
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;

    private float nextJumpImpulse = 0f;

    private float walkSpeed = 5f;
    private float walkImpulseFactor = 10f;

    //recycled objects
    private final com.jme3.math.Vector3f positionStore = new com.jme3.math.Vector3f();
    private final Vector3f jomlPositionStore = new Vector3f();

    private final com.jme3.math.Vector3f[] vectorsStack = new com.jme3.math.Vector3f[16];
    private final Vector3f[] vectorsJomlStack = new Vector3f[16];

    {
        for (int i = 0; i < vectorsStack.length; i++) {
            vectorsStack[i] = new com.jme3.math.Vector3f();
        }
        for (int i = 0; i < vectorsJomlStack.length; i++) {
            vectorsJomlStack[i] = new Vector3f();
        }
    }

    public DynamicCharacterController(float radius, float totalHeight, float crouchTotalHeight, float mass) {
        this.totalHeight = totalHeight;
        this.crouchTotalHeight = crouchTotalHeight;
        this.radius = radius;

        {
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(radius, totalHeight - (radius * 2f));
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, totalHeight * 0.5f, 0f);
            this.collisionShape = compound;
        }

        {
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(radius, crouchTotalHeight - (radius * 2f));
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, crouchTotalHeight * 0.5f, 0f);
            this.crouchCollisionShape = compound;
        }

        this.rigidBody = new PhysicsRigidBody(this.collisionShape, mass);
        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setEnableSleep(false);
        this.rigidBody.setFriction(0f);
        this.rigidBody.setRestitution(0f);
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addTickListener(this);
        this.space.addCollisionObject(this.rigidBody);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeTickListener(this);
        this.space.removeCollisionObject(this.rigidBody);
        this.space = null;
    }

    public PhysicsSpace getPhysicsSpace() {
        return space;
    }

    public float getTotalHeight() {
        return totalHeight;
    }

    public float getCrouchTotalHeight() {
        return crouchTotalHeight;
    }

    public float getHeight() {
        if (isCrouched()) {
            return getCrouchTotalHeight();
        }
        return getTotalHeight();
    }

    public float getRadius() {
        return radius;
    }
    
    public CollisionShape getCollisionShape() {
        return this.collisionShape;
    }

    public CollisionShape getCrouchCollisionShape() {
        return this.crouchCollisionShape;
    }

    public PhysicsRigidBody getRigidBody() {
        return this.rigidBody;
    }

    public Vector3fc getPosition() {
        this.rigidBody.getPhysicsLocation(this.positionStore);
        this.jomlPositionStore.set(this.positionStore.x, this.positionStore.y, this.positionStore.z);
        return this.jomlPositionStore;
    }

    public void setPosition(float x, float y, float z) {
        this.positionStore.set(x, y, z);
        this.rigidBody.setPhysicsLocation(this.positionStore);
        this.jomlPositionStore.set(x, y, z);
    }

    public void setPosition(Vector3fc position) {
        setPosition(position.x(), position.y(), position.z());
    }

    public boolean isNoclipEnabled() {
        return noclipEnabled;
    }

    public void setNoclipEnabled(boolean noclipEnabled) {
        this.noclipEnabled = noclipEnabled;
        this.noclipStateChanged = true;
    }

    public boolean isCrouched() {
        return crouched;
    }

    public void setCrouched(boolean crouched) {
        this.crouchStateChanged = (this.crouched != crouched);
    }

    public boolean onGround() {
        return true;
    }

    public float getWalkDirectionX() {
        return walkDirectionX;
    }

    public float getWalkDirectionZ() {
        return walkDirectionZ;
    }

    public void setWalkDirection(float x, float z) {
        if (x != 0f && z != 0f) {
            float invlength = 1f / ((float) Math.sqrt((x * x) + (z * z)));
            x *= invlength;
            z *= invlength;
        }
        this.walkDirectionX = x;
        this.walkDirectionZ = z;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    public float getWalkImpulseFactor() {
        return walkImpulseFactor;
    }

    public void setWalkImpulseFactor(float walkImpulseFactor) {
        this.walkImpulseFactor = walkImpulseFactor;
    }

    public void jump(float speed) {
        this.nextJumpImpulse += speed * this.rigidBody.getMass();
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        int stack = 0;

        if (this.nextJumpImpulse != 0f) {
            this.rigidBody.applyCentralImpulse(this.vectorsStack[stack++].set(
                    0f, this.nextJumpImpulse, 0f
            ));
            this.nextJumpImpulse = 0f;
        }
        
        if (this.walkDirectionX != 0f && this.walkDirectionZ != 0f) {
            float mass = this.rigidBody.getMass();
            
            float impulseX = this.walkDirectionX * mass * this.walkImpulseFactor * timeStep;
            float impulseY = 0f;
            float impulseZ = this.walkDirectionZ * mass * this.walkImpulseFactor * timeStep;
            
            this.rigidBody.applyCentralImpulse(this.vectorsStack[stack++].set(
                    impulseX, impulseY, impulseZ
            ));
        }
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        
    }

}
