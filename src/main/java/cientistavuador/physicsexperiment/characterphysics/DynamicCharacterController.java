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

    public static final float GROUND_TEST_OFFSET = 0.1f;
    public static final float GRAVITY_CUTOFF_TIME = 0.25f;

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

    private final PhysicsGhostObject groundTest;

    //character state
    private boolean noclipEnabled = false;
    private boolean noclipStateChanged = false;

    private boolean crouched = false;
    private boolean crouchStateChanged = false;

    private boolean onGround = false;
    private final Vector3f groundNormal = new Vector3f();
    private final Vector3f groundOrientedWalkDirection = new Vector3f();

    //character movement state
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;
    private float walkDirectionSpeed = 0f;
    private float movementHardness = 8f;
    private float slopeThreshold = 0.75f;
    private float friction = 4f;
    private float nextJumpImpulse = 0f;

    private float appliedWalkSpeedX = 0f;
    private float appliedWalkSpeedY = 0f;
    private float appliedWalkSpeedZ = 0f;

    private float lastSpeedX = 0f;
    private float lastSpeedY = 0f;
    private float lastSpeedZ = 0f;

    private float walkSpeedX = 0f;
    private float walkSpeedY = 0f;
    private float walkSpeedZ = 0f;

    private float groundTime = 0f;

    //recycled objects
    private final com.jme3.math.Vector3f speedApply = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f speedGetJme = new com.jme3.math.Vector3f();
    private final Vector3f speedGet = new Vector3f();

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
        float r = this.collisionShape.maxRadius();
        this.rigidBody.setCcdSweptSphereRadius(r);
        this.rigidBody.setCcdMotionThreshold(r);
        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setEnableSleep(false);
        this.rigidBody.setFriction(0f);
        this.rigidBody.setRestitution(0f);

        {
            SphereCollisionShape sphere = new SphereCollisionShape(radius * 0.95f);
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(sphere, 0f, sphere.getRadius() - GROUND_TEST_OFFSET, 0f);
            this.groundTest = new PhysicsGhostObject(compound);
        }
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addTickListener(this);
        this.space.addCollisionObject(this.rigidBody);
        this.space.addCollisionObject(this.groundTest);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeTickListener(this);
        this.space.removeCollisionObject(this.rigidBody);
        this.space.removeCollisionObject(this.groundTest);
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
        return this.onGround;
    }

    public Vector3fc getGroundNormal() {
        return groundNormal;
    }

    public Vector3fc getGroundOrientedWalkDirection() {
        return groundOrientedWalkDirection;
    }

    public float getWalkDirectionX() {
        return walkDirectionX * this.walkDirectionSpeed;
    }

    public float getWalkDirectionZ() {
        return walkDirectionZ * this.walkDirectionSpeed;
    }

    public void setWalkDirection(float x, float z) {
        float length = ((float) Math.sqrt((x * x) + (z * z)));
        if (length == 0f) {
            this.walkDirectionSpeed = 0f;
            this.walkDirectionX = 0f;
            this.walkDirectionZ = 0f;
        } else {
            this.walkDirectionSpeed = length;
            float inv = 1f / length;
            this.walkDirectionX = x * inv;
            this.walkDirectionZ = z * inv;
        }
    }

    public float getMovementHardness() {
        return movementHardness;
    }

    public void setMovementHardness(float movementHardness) {
        this.movementHardness = movementHardness;
    }

    public float getSlopeThreshold() {
        return slopeThreshold;
    }

    public void setSlopeThreshold(float slopeThreshold) {
        this.slopeThreshold = slopeThreshold;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public void jump(float speed) {
        this.nextJumpImpulse += speed;
    }

    private void setGhostPositions(Vector3fc position) {
        int stack = 0;

        this.groundTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(), position.y(), position.z()
        ));
    }

    private void applySpeed(float x, float y, float z) {
        float mass = this.rigidBody.getMass();
        this.speedApply.set(x * mass, y * mass, z * mass);
        this.rigidBody.applyCentralImpulse(this.speedApply);
    }

    private Vector3fc rigidBodySpeed() {
        this.rigidBody.getLinearVelocity(this.speedGetJme);
        this.speedGet.set(
                this.speedGetJme.x,
                this.speedGetJme.y,
                this.speedGetJme.z
        );
        return this.speedGet;
    }

    private void applyJumpImpulse() {
        if (this.nextJumpImpulse != 0f) {
            applySpeed(0f, this.nextJumpImpulse, 0f);
            this.nextJumpImpulse = 0f;
        }
    }

    private void findGroundNormal(PhysicsSpace space, Vector3fc position) {
        int stack = 0;

        this.groundNormal.set(0f, 1f, 0f);
        if (onGround()) {
            List<PhysicsRayTestResult> results = space.rayTest(
                    this.vectorsStack[stack++].set(
                            position.x(), position.y(), position.z()
                    ),
                    this.vectorsStack[stack++].set(
                            position.x(), position.y() - 1f, position.z()
                    )
            );
            for (PhysicsRayTestResult e : results) {
                if (e == null || e.getCollisionObject().equals(this.rigidBody) || e.getCollisionObject() instanceof PhysicsGhostObject) {
                    continue;
                }
                com.jme3.math.Vector3f n = e.getHitNormalLocal(this.vectorsStack[stack++]);
                this.groundNormal.set(
                        n.x,
                        n.y,
                        n.z
                );
                break;
            }
            if (this.groundNormal.dot(0f, 1f, 0f) < this.slopeThreshold) {
                this.groundNormal.set(0f, 1f, 0f);
            }
        }
    }

    private void calculateGroundOrientedDirection() {
        int jomlStack = 0;

        this.groundOrientedWalkDirection.set(0f, 0f, 0f);
        if (this.walkDirectionX != 0f && this.walkDirectionZ != 0f) {
            Vector3f walkDir = this.vectorsJomlStack[jomlStack++]
                    .set(this.walkDirectionX, 0f, this.walkDirectionZ);

            Vector3fc normal = this.groundNormal;
            Vector3f tangent = walkDir.cross(normal, this.vectorsJomlStack[jomlStack++]).normalize();
            Vector3f bitangent = normal.cross(tangent, this.vectorsJomlStack[jomlStack++]).normalize();

            this.groundOrientedWalkDirection.set(bitangent);
        }
    }

    private void applyWalkSpeed(float timeStep) {
        float targetSpeedX = this.groundOrientedWalkDirection.x() * this.walkDirectionSpeed;
        float targetSpeedY = this.groundOrientedWalkDirection.y() * this.walkDirectionSpeed;
        float targetSpeedZ = this.groundOrientedWalkDirection.z() * this.walkDirectionSpeed;

        float deltaSpeedX = targetSpeedX - this.walkSpeedX;
        float deltaSpeedY = targetSpeedY - this.walkSpeedY;
        float deltaSpeedZ = targetSpeedZ - this.walkSpeedZ;
        deltaSpeedX *= timeStep * this.movementHardness;
        deltaSpeedY *= timeStep * this.movementHardness;
        deltaSpeedZ *= timeStep * this.movementHardness;

        this.appliedWalkSpeedX = deltaSpeedX;
        this.appliedWalkSpeedY = deltaSpeedY;
        this.appliedWalkSpeedZ = deltaSpeedZ;

        applySpeed(deltaSpeedX, deltaSpeedY, deltaSpeedZ);

        Vector3fc speed = rigidBodySpeed();
        this.lastSpeedX = speed.x();
        this.lastSpeedY = speed.y();
        this.lastSpeedZ = speed.z();
    }

    private void removeGravityIfNeeded(float timeStep) {
        if (onGround()) {
            this.groundTime += timeStep;
            if (this.groundTime > GRAVITY_CUTOFF_TIME) {
                this.rigidBody.clearForces();
            }
        } else {
            this.groundTime = 0f;
        }
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        Vector3fc position = getPosition();

        setGhostPositions(position);
        findGroundNormal(space, position);
        calculateGroundOrientedDirection();

        applyJumpImpulse();
        applyWalkSpeed(timeStep);

        removeGravityIfNeeded(timeStep);
    }

    private boolean checkGhostCollision(PhysicsSpace space, PhysicsGhostObject p) {
        boolean result = false;
        for (PhysicsCollisionObject o : p.getOverlappingObjects()) {
            if (o == null || o.equals(this.rigidBody) || o instanceof PhysicsGhostObject) {
                continue;
            }
            result = (space.pairTest(p, o, null) != 0);
            if (result) {
                break;
            }
        }
        return result;
    }

    private float clamp(float speed, float max) {
        if (Math.signum(speed) != Math.signum(max)) {
            speed = 0f;
        } else if (Math.abs(speed) > Math.abs(max)) {
            speed = max;
        }
        return speed;
    }

    private void collectWalkSpeedResults() {
        Vector3fc speed = rigidBodySpeed();

        float dX = this.lastSpeedX - speed.x();
        float dY = this.lastSpeedY - speed.y();
        float dZ = this.lastSpeedZ - speed.z();

        float appliedDeltaX = this.appliedWalkSpeedX - dX;
        float appliedDeltaY = this.appliedWalkSpeedY - dY;
        float appliedDeltaZ = this.appliedWalkSpeedZ - dZ;

        appliedDeltaX = clamp(appliedDeltaX, this.appliedWalkSpeedX);
        appliedDeltaY = clamp(appliedDeltaY, this.appliedWalkSpeedY);
        appliedDeltaZ = clamp(appliedDeltaZ, this.appliedWalkSpeedZ);

        this.walkSpeedX += appliedDeltaX;
        this.walkSpeedY += appliedDeltaY;
        this.walkSpeedZ += appliedDeltaZ;

        this.walkSpeedX = clamp(this.walkSpeedX, speed.x());
        this.walkSpeedY = clamp(this.walkSpeedY, speed.y());
        this.walkSpeedZ = clamp(this.walkSpeedZ, speed.z());
    }

    private void applyFriction(float timestep) {
        Vector3fc speed = rigidBodySpeed();

        float dX = speed.x() - this.walkSpeedX;
        float dY = speed.y() - this.walkSpeedY;
        float dZ = speed.z() - this.walkSpeedZ;

        float fX = -dX * timestep * this.friction;
        float fY = -dY * timestep * this.friction;
        float fZ = -dZ * timestep * this.friction;

        if (Math.signum(fX + dX) != Math.signum(dX)) {
            fX = -dX;
        }
        if (Math.signum(fY + dY) != Math.signum(dY)) {
            fY = -dY;
        }
        if (Math.signum(fZ + dZ) != Math.signum(dZ)) {
            fZ = -dZ;
        }

        applySpeed(fX, fY, fZ);
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        this.onGround = checkGhostCollision(space, this.groundTest);

        collectWalkSpeedResults();
        applyFriction(timeStep);
    }

}
