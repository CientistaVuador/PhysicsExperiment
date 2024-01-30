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
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
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
    public static final float GROUND_NORMAL_RAY_OFFSET = 0.2f;
    public static final float GRAVITY_CUTOFF_TIME = 0.25f;

    private static final Vector3f[] GROUND_NORMAL_OFFSETS = new Vector3f[]{
        new Vector3f(0f, 0f, 0f),
        
        new Vector3f(1f, 0f, 0f),
        new Vector3f(-1f, 0f, 0f),
        
        new Vector3f(0f, 0f, 1f),
        new Vector3f(0f, 0f, -1f),
        
        new Vector3f(1f, 0f, 1f).normalize(0.5f),
        new Vector3f(-1f, 0f, -1f).normalize(0.5f),
        new Vector3f(-1f, 0f, 1f).normalize(0.5f),
        new Vector3f(1f, 0f, -1f).normalize(0.5f)
    };

    //physics space
    private PhysicsSpace space = null;

    //character info
    private final float totalHeight;
    private final float crouchTotalHeight;
    private final float radius;

    //character collision
    private final CapsuleCollisionShape rawCollisionShape;
    private final CapsuleCollisionShape rawCrouchCollisionShape;
    
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

    //character movement configuration
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;
    private float walkDirectionSpeed = 0f;

    private float gravityMultiplier = 2f;

    private float airMovementRoughness = 1f;
    private float groundMovementRoughness = 10f;

    private float slopeThreshold = 0.75f;

    private float airFriction = 0.5f;
    private float groundFriction = 8f;

    private float nextJumpImpulse = 0f;

    //character movement state
    private float deltaSpeedX = 0f;
    private float deltaSpeedY = 0f;
    private float deltaSpeedZ = 0f;

    private float internalSpeedX = 0f;
    private float internalSpeedY = 0f;
    private float internalSpeedZ = 0f;

    private float externalSpeedX = 0f;
    private float externalSpeedY = 0f;
    private float externalSpeedZ = 0f;

    private float appliedWalkSpeedX = 0f;
    private float appliedWalkSpeedY = 0f;
    private float appliedWalkSpeedZ = 0f;

    private float appliedJumpSpeed = 0f;

    private float appliedGravityX = 0f;
    private float appliedGravityY = 0f;
    private float appliedGravityZ = 0f;

    private float walkSpeedX = 0f;
    private float walkSpeedY = 0f;
    private float walkSpeedZ = 0f;

    private float jumpSpeed = 0f;

    private float gravitySpeedX = 0f;
    private float gravitySpeedY = 0f;
    private float gravitySpeedZ = 0f;

    private float internalGravityMultiplier = 1f;
    private float gravityGroundCounter = 0f;

    //recycled objects
    private final com.jme3.math.Vector3f speedApply = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f speedGetJme = new com.jme3.math.Vector3f();
    private final Vector3f speedGet = new Vector3f();

    private final com.jme3.math.Vector3f spaceGravity = new com.jme3.math.Vector3f();
    private final Vector3f spaceGravityGet = new Vector3f();

    private final com.jme3.math.Vector3f positionStore = new com.jme3.math.Vector3f();
    private final Vector3f jomlPositionStore = new Vector3f();

    private final com.jme3.math.Vector3f rayPositionA = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f rayPositionB = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f rayNormal = new com.jme3.math.Vector3f();

    private final Vector3f normalSum = new Vector3f();

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
            this.rawCollisionShape = capsule;
            this.collisionShape = compound;
        }

        {
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(radius, crouchTotalHeight - (radius * 2f));
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, crouchTotalHeight * 0.5f, 0f);
            this.rawCrouchCollisionShape = capsule;
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
        this.rigidBody.setProtectGravity(true);
        this.rigidBody.setGravity(com.jme3.math.Vector3f.ZERO);

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

    public float getGravityMultiplier() {
        return gravityMultiplier;
    }

    public void setGravityMultiplier(float gravityMultiplier) {
        this.gravityMultiplier = gravityMultiplier;
    }

    public float getAirMovementRoughness() {
        return airMovementRoughness;
    }

    public void setAirMovementRoughness(float airMovementRoughness) {
        this.airMovementRoughness = airMovementRoughness;
    }

    public float getGroundMovementRoughness() {
        return groundMovementRoughness;
    }

    public void setGroundMovementRoughness(float groundMovementRoughness) {
        this.groundMovementRoughness = groundMovementRoughness;
    }

    public float getSlopeThreshold() {
        return slopeThreshold;
    }

    public void setSlopeThreshold(float slopeThreshold) {
        this.slopeThreshold = slopeThreshold;
    }

    public float getAirFriction() {
        return airFriction;
    }

    public void setAirFriction(float airFriction) {
        this.airFriction = airFriction;
    }

    public float getGroundFriction() {
        return groundFriction;
    }

    public void setGroundFriction(float groundFriction) {
        this.groundFriction = groundFriction;
    }

    public void jump(float speed) {
        this.nextJumpImpulse += speed;
    }

    public boolean isJumping() {
        return this.jumpSpeed != 0f;
    }

    public void checkedJump(float speed, float crouchSpeed) {
        if (!onGround() || isJumping() || isNoclipEnabled()) {
            return;
        }
        if (isCrouched()) {
            jump(crouchSpeed);
        } else {
            jump(speed);
        }
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

    private Vector3fc spaceGravity(PhysicsSpace space) {
        space.getGravity(this.spaceGravity);
        this.spaceGravityGet.set(
                this.spaceGravity.x,
                this.spaceGravity.y,
                this.spaceGravity.z
        );
        return this.spaceGravityGet;
    }

    private com.jme3.math.Vector3f getGroundNormal(PhysicsSpace space, Vector3fc position, float offsetX, float offsetY, float offsetZ) {
        List<PhysicsRayTestResult> results = space.rayTest(
                this.rayPositionA.set(
                        position.x() + offsetX,
                        position.y() + offsetY + (this.radius * 2f),
                        position.z() + offsetZ
                ),
                this.rayPositionB.set(
                        position.x() + offsetX,
                        position.y() + offsetY - GROUND_NORMAL_RAY_OFFSET,
                        position.z() + offsetZ
                )
        );
        com.jme3.math.Vector3f result = null;
        for (PhysicsRayTestResult e : results) {
            if (e == null || e.getCollisionObject().equals(this.rigidBody) || e.getCollisionObject() instanceof PhysicsGhostObject) {
                continue;
            }
            result = e.getHitNormalLocal(this.rayNormal);
            break;
        }
        return result;
    }

    private void findGroundNormal(PhysicsSpace space, Vector3fc position) {
        this.groundNormal.set(0f, 1f, 0f);
        if (onGround()) {
            this.normalSum.zero();
            float added = 0f;
            for (Vector3fc e : GROUND_NORMAL_OFFSETS) {
                float offsetX = e.x() * this.radius;
                float offsetY = e.y() * this.radius;
                float offsetZ = e.z() * this.radius;

                com.jme3.math.Vector3f normal = getGroundNormal(
                        space,
                        position,
                        offsetX, offsetY, offsetZ
                );

                if (normal != null) {
                    this.normalSum.add(
                            normal.x,
                            normal.y,
                            normal.z
                    );
                    added++;
                }
            }

            if (added != 0f) {
                this.normalSum.div(added, this.groundNormal);
                if (this.groundNormal.dot(0f, 1f, 0f) < this.slopeThreshold) {
                    this.groundNormal.set(0f, 1f, 0f);
                }
            } else {
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

    private void disableGravityIfNeeded(float timeStep) {
        if (!onGround()) {
            this.gravityGroundCounter = 0f;
            this.internalGravityMultiplier = this.gravityMultiplier;
            return;
        }
        this.gravityGroundCounter += timeStep;
        if (this.gravityGroundCounter >= GRAVITY_CUTOFF_TIME) {
            this.gravityGroundCounter = GRAVITY_CUTOFF_TIME;
            this.internalGravityMultiplier = 0f;
        }
    }

    private void applyWalkSpeed(float timeStep) {
        float movementRoughness = (onGround() ? getGroundMovementRoughness() : getAirMovementRoughness());

        float targetSpeedX = this.groundOrientedWalkDirection.x() * this.walkDirectionSpeed;
        float targetSpeedY = this.groundOrientedWalkDirection.y() * this.walkDirectionSpeed;
        float targetSpeedZ = this.groundOrientedWalkDirection.z() * this.walkDirectionSpeed;

        float deltaX = targetSpeedX - this.walkSpeedX;
        float deltaY = targetSpeedY - this.walkSpeedY;
        float deltaZ = targetSpeedZ - this.walkSpeedZ;

        float deltaXStep = deltaX * timeStep * movementRoughness;
        float deltaYStep = deltaY * timeStep * movementRoughness;
        float deltaZStep = deltaZ * timeStep * movementRoughness;
        
        if (Math.abs(deltaXStep) > Math.abs(deltaX)) {
            deltaXStep = deltaX;
        }
        if (Math.abs(deltaYStep) > Math.abs(deltaY)) {
            deltaYStep = deltaY;
        }
        if (Math.abs(deltaZStep) > Math.abs(deltaZ)) {
            deltaZStep = deltaZ;
        }
        
        this.appliedWalkSpeedX = deltaXStep;
        this.appliedWalkSpeedY = deltaYStep;
        this.appliedWalkSpeedZ = deltaZStep;

        applySpeed(deltaXStep, deltaYStep, deltaZStep);
    }

    private void applyJump() {
        applySpeed(0f, this.nextJumpImpulse, 0f);
        this.appliedJumpSpeed = this.nextJumpImpulse;
        this.nextJumpImpulse = 0f;
    }

    private void applyGravity(PhysicsSpace space, float timeStep) {
        Vector3fc gravity = spaceGravity(space);

        this.appliedGravityX = gravity.x() * timeStep * this.internalGravityMultiplier;
        this.appliedGravityY = gravity.y() * timeStep * this.internalGravityMultiplier;
        this.appliedGravityZ = gravity.z() * timeStep * this.internalGravityMultiplier;

        applySpeed(this.appliedGravityX, this.appliedGravityY, this.appliedGravityZ);
    }

    private void storeDeltaSpeed() {
        Vector3fc speed = rigidBodySpeed();
        this.deltaSpeedX = speed.x();
        this.deltaSpeedY = speed.y();
        this.deltaSpeedZ = speed.z();
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        Vector3fc position = getPosition();

        setGhostPositions(position);
        findGroundNormal(space, position);
        calculateGroundOrientedDirection();

        disableGravityIfNeeded(timeStep);

        applyWalkSpeed(timeStep);
        applyJump();
        applyGravity(space, timeStep);

        storeDeltaSpeed();
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

    private void collectDeltaSpeed() {
        Vector3fc speed = rigidBodySpeed();
        this.deltaSpeedX -= speed.x();
        this.deltaSpeedY -= speed.y();
        this.deltaSpeedZ -= speed.z();
    }

    private void resetInternalSpeed() {
        this.internalSpeedX = 0f;
        this.internalSpeedY = 0f;
        this.internalSpeedZ = 0f;
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
        float appliedDeltaX = this.appliedWalkSpeedX - this.deltaSpeedX;
        float appliedDeltaY = this.appliedWalkSpeedY - this.deltaSpeedY;
        float appliedDeltaZ = this.appliedWalkSpeedZ - this.deltaSpeedZ;

        appliedDeltaX = clamp(appliedDeltaX, this.appliedWalkSpeedX);
        appliedDeltaY = clamp(appliedDeltaY, this.appliedWalkSpeedY);
        appliedDeltaZ = clamp(appliedDeltaZ, this.appliedWalkSpeedZ);

        this.walkSpeedX += appliedDeltaX;
        this.walkSpeedY += appliedDeltaY;
        this.walkSpeedZ += appliedDeltaZ;

        Vector3fc speed = rigidBodySpeed();

        this.walkSpeedX = clamp(this.walkSpeedX, speed.x() - this.internalSpeedX);
        this.walkSpeedY = clamp(this.walkSpeedY, speed.y() - this.internalSpeedY);
        this.walkSpeedZ = clamp(this.walkSpeedZ, speed.z() - this.internalSpeedZ);

        this.internalSpeedX += this.walkSpeedX;
        this.internalSpeedY += this.walkSpeedY;
        this.internalSpeedZ += this.walkSpeedZ;
    }

    private void collectJumpSpeedResults() {
        float appliedDeltaJump = this.appliedJumpSpeed - this.deltaSpeedY;
        appliedDeltaJump = clamp(appliedDeltaJump, this.appliedJumpSpeed);
        this.jumpSpeed += appliedDeltaJump;

        Vector3fc speed = rigidBodySpeed();
        this.jumpSpeed = clamp(this.jumpSpeed, speed.y() - this.internalSpeedY);

        this.internalSpeedY += this.jumpSpeed;
    }

    private void collectGravitySpeedResults() {
        float appliedDeltaX = this.appliedGravityX - this.deltaSpeedX;
        float appliedDeltaY = this.appliedGravityY - this.deltaSpeedY;
        float appliedDeltaZ = this.appliedGravityZ - this.deltaSpeedZ;

        appliedDeltaX = clamp(appliedDeltaX, this.appliedGravityX);
        appliedDeltaY = clamp(appliedDeltaY, this.appliedGravityY);
        appliedDeltaZ = clamp(appliedDeltaZ, this.appliedGravityZ);

        this.gravitySpeedX += appliedDeltaX;
        this.gravitySpeedY += appliedDeltaY;
        this.gravitySpeedZ += appliedDeltaZ;

        Vector3fc speed = rigidBodySpeed();

        this.gravitySpeedX = clamp(this.gravitySpeedX, speed.x() - this.internalSpeedX);
        this.gravitySpeedY = clamp(this.gravitySpeedY, speed.y() - this.internalSpeedY);
        this.gravitySpeedZ = clamp(this.gravitySpeedZ, speed.z() - this.internalSpeedZ);

        this.internalSpeedX += this.gravitySpeedX;
        this.internalSpeedY += this.gravitySpeedY;
        this.internalSpeedZ += this.gravitySpeedZ;
    }

    private void collectExternalSpeed() {
        Vector3fc speed = rigidBodySpeed();

        this.externalSpeedX = speed.x() - this.internalSpeedX;
        this.externalSpeedY = speed.y() - this.internalSpeedY;
        this.externalSpeedZ = speed.z() - this.internalSpeedZ;
    }

    private void applyFriction(float timestep) {
        float friction = (onGround() ? getGroundFriction() : getAirFriction());

        float dX = this.externalSpeedX;
        float dY = this.externalSpeedY;
        float dZ = this.externalSpeedZ;

        float fX = -dX * timestep * friction;
        float fY = -dY * timestep * friction;
        float fZ = -dZ * timestep * friction;

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

        this.externalSpeedX += fX;
        this.externalSpeedY += fY;
        this.externalSpeedZ += fZ;
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        this.onGround = checkGhostCollision(space, this.groundTest);

        collectDeltaSpeed();

        resetInternalSpeed();

        collectWalkSpeedResults();
        collectJumpSpeedResults();
        collectGravitySpeedResults();

        collectExternalSpeed();

        applyFriction(timeStep);
    }

}
