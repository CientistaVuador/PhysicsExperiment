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
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class CharacterController implements PhysicsTickListener {

    public static final float GROUND_EPSILON = 0.05f;

    private PhysicsSpace space = null;

    private final float totalHeight;
    private final float radius;
    private final float mass;
    private final PhysicsRigidBody rigidBody;
    private final PhysicsGhostObject groundTest;

    private float gravity = -9.8f;
    private float jumpSpeed = 8f;

    private float walkX = 0f;
    private float walkZ = 0f;
    private float fallSpeed = 0f;
    private float currentJumpSpeed = 0f;

    private boolean noclipEnabled = false;
    private boolean onGround = false;

    private final com.jme3.math.Vector3f positionStore = new com.jme3.math.Vector3f();
    private final Vector3f jomlPositionStore = new Vector3f();
    private final com.jme3.math.Vector3f velocityStore = new com.jme3.math.Vector3f();

    public CharacterController(float totalHeight, float radius, float mass) {
        this.totalHeight = totalHeight;
        this.radius = radius;
        this.mass = mass;

        CapsuleCollisionShape capsule = new CapsuleCollisionShape(
                radius,
                totalHeight - (radius * 2f)
        );
        CompoundCollisionShape compound = new CompoundCollisionShape(1);
        compound.addChildShape(capsule, 0f, totalHeight / 2f, 0f);
        this.rigidBody = new PhysicsRigidBody(compound, mass);
        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setEnableSleep(false);
        this.rigidBody.setProtectGravity(true);
        this.rigidBody.setGravity(com.jme3.math.Vector3f.ZERO);

        float sphereFactor = 0.95f;
        SphereCollisionShape sphere = new SphereCollisionShape(radius * sphereFactor);
        CompoundCollisionShape sphereCompound = new CompoundCollisionShape(1);
        sphereCompound.addChildShape(sphere, 0f, radius * sphereFactor, 0f);
        this.groundTest = new PhysicsGhostObject(sphereCompound);
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addCollisionObject(this.rigidBody);
        this.space.addCollisionObject(this.groundTest);
        this.space.addTickListener(this);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeCollisionObject(this.rigidBody);
        this.space.removeCollisionObject(this.groundTest);
        this.space.removeTickListener(this);
        this.space = null;
    }

    public PhysicsSpace getPhysicsSpace() {
        return space;
    }

    public float getTotalHeight() {
        return totalHeight;
    }

    public float getRadius() {
        return radius;
    }

    public float getMass() {
        return mass;
    }

    public PhysicsRigidBody getRigidBody() {
        return rigidBody;
    }

    public float getGravity() {
        return gravity;
    }

    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    public float getJumpSpeed() {
        return jumpSpeed;
    }

    public void setJumpSpeed(float jumpSpeed) {
        this.jumpSpeed = jumpSpeed;
    }

    public float getWalkX() {
        return walkX;
    }

    public float getWalkZ() {
        return walkZ;
    }

    public void setWalkDirection(float x, float z) {
        this.walkX = x;
        this.walkZ = z;
    }

    public boolean isNoclipEnabled() {
        return noclipEnabled;
    }

    public void setNoclipEnabled(boolean noclipEnabled) {
        this.noclipEnabled = noclipEnabled;
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

    public boolean onGround() {
        return this.onGround;
    }

    public void jump() {
        this.currentJumpSpeed += this.jumpSpeed;
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        if (this.noclipEnabled) {
            this.rigidBody.setLinearVelocity(com.jme3.math.Vector3f.ZERO);
            this.rigidBody.setContactResponse(false);
            this.fallSpeed = 0f;
            this.currentJumpSpeed = 0f;
            return;
        }
        
        Vector3fc position = getPosition();
        this.groundTest.setPhysicsLocation(new com.jme3.math.Vector3f(
                position.x(),
                position.y() - GROUND_EPSILON,
                position.z()
        ));

        this.rigidBody.setContactResponse(true);

        if (!onGround()) {
            this.fallSpeed += this.gravity * timeStep;
        } else {
            this.fallSpeed = 0f;
        }

        this.currentJumpSpeed += this.gravity * timeStep;
        if (this.currentJumpSpeed < 0f) {
            this.currentJumpSpeed = 0f;
        }

        this.velocityStore.set(
                this.walkX,
                this.fallSpeed + this.currentJumpSpeed,
                this.walkZ
        );
        this.rigidBody.setLinearVelocity(this.velocityStore);
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        this.onGround = false;
        for (PhysicsCollisionObject o : this.groundTest.getOverlappingObjects()) {
            if (o == null || o.equals(this.rigidBody) || o instanceof PhysicsGhostObject) {
                continue;
            }
            space.pairTest(this.groundTest, o, (event) -> {
                this.onGround = true;
            });
            if (this.onGround) {
                break;
            }
        }
    }

}
