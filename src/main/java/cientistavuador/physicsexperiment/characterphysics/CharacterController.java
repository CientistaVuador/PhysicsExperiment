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
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class CharacterController implements PhysicsTickListener {

    public static final float EPSILON = 0.00001f;

    public static final float GROUND_PULL_BOX_HEIGHT = 0.05f;

    public static final float UNCROUCH_TEST_RADIUS_NEGATIVE_MARGIN = 0.01f;

    public static final float GROUND_TEST_RADIUS_NEGATIVE_MARGIN = 0.025f;
    public static final float GROUND_TEST_HEIGHT = 0.082f;

    public static final float GROUND_NORMAL_RAY_OFFSET = 1f;

    public static final float GRAVITY_CUTOFF_TIME = 0.1f;

    private static final Vector3f[] GROUND_NORMAL_OFFSETS = new Vector3f[]{
        new Vector3f(0f, 0f, 0f),
        new Vector3f(1f, 0f, 0f),
        new Vector3f(-1f, 0f, 0f),
        new Vector3f(0f, 0f, 1f),
        new Vector3f(0f, 0f, -1f)
    };

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
    
    private final BoxCollisionShape contactTestBox;
    private final PhysicsGhostObject contactTest;

    //character state
    private boolean noclipEnabled = false;
    private boolean noclipStateChanged = false;

    private boolean crouched = false;
    private boolean crouchStateChanged = false;
    private boolean airCrouched = false;

    private boolean onGround = false;
    private boolean pullingToTheGround = false;
    private float lastGroundPullHeight = 0f;

    private final Vector3f groundNormal = new Vector3f();
    private final Vector3f groundOrientedWalkDirection = new Vector3f();

    //character movement configuration
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;
    private float walkDirectionSpeed = 0f;

    private float gravityCoefficient = 2.5f;

    private float airMovementRoughness = 3f;
    private float groundMovementRoughness = 10f;

    private float slopeThreshold = 0.75f;

    private float airFriction = 1f;
    private float groundFriction = 6f;
    
    private float maxGroundPullDistance = 0.60f;
    private float groundPullSpeed = -3.2f;
    private float groundPullMaxExternalSpeed = 2f;

    private float nextJumpImpulse = 0f;

    //applied velocities
    private float appliedWalkX = 0f;
    private float appliedWalkY = 0f;
    private float appliedWalkZ = 0f;

    private float appliedJump = 0f;

    private float appliedGravityX = 0f;
    private float appliedGravityY = 0f;
    private float appliedGravityZ = 0f;

    //total applied velocities
    private float appliedTotalX = 0f;
    private float appliedTotalY = 0f;
    private float appliedTotalZ = 0f;

    //velocity delta
    private float deltaX = 0f;
    private float deltaY = 0f;
    private float deltaZ = 0f;

    //velocities
    private float walkX = 0f;
    private float walkY = 0f;
    private float walkZ = 0f;

    private float jump = 0f;

    private float gravityX = 0f;
    private float gravityY = 0f;
    private float gravityZ = 0f;

    private float internalX = 0f;
    private float internalY = 0f;
    private float internalZ = 0f;

    private float externalX = 0f;
    private float externalY = 0f;
    private float externalZ = 0f;

    //gravity
    private float internalGravityMultiplier = this.gravityCoefficient;
    private float gravityGroundCounter = 0f;

    //recycled objects
    private final com.jme3.math.Vector3f contactTestPosition = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f contactTestScale = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f groundPullStartPosition = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f groundPullEndPosition = new com.jme3.math.Vector3f();

    private final com.jme3.math.Transform groundPullStart = new Transform();
    private final com.jme3.math.Transform groundPullEnd = new Transform();

    private final com.jme3.math.Vector3f velocityApply = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f velocityGetJme = new com.jme3.math.Vector3f();
    private final Vector3f velocityGet = new Vector3f();

    private final com.jme3.math.Vector3f spaceGravity = new com.jme3.math.Vector3f();
    private final Vector3f spaceGravityGet = new Vector3f();

    private final com.jme3.math.Vector3f positionStore = new com.jme3.math.Vector3f();
    private final Vector3f jomlPositionStore = new Vector3f();

    private final com.jme3.math.Vector3f rayPositionA = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f rayPositionB = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f rayNormal = new com.jme3.math.Vector3f();

    private final Vector3f normalSum = new Vector3f();
    
    private final Vector3f orientedWalk = new Vector3f();
    private final Vector3f orientedTangent = new Vector3f();
    private final Vector3f orientedBitangent = new Vector3f();
    
    public CharacterController(float radius, float totalHeight, float crouchTotalHeight, float mass) {
        this.totalHeight = totalHeight;
        this.crouchTotalHeight = crouchTotalHeight;
        this.radius = radius;

        {
            BoxCollisionShape box = new BoxCollisionShape(radius, totalHeight * 0.5f, radius);
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box, 0f, totalHeight * 0.5f, 0f);
            this.collisionShape = compound;
        }

        {
            BoxCollisionShape box = new BoxCollisionShape(radius, crouchTotalHeight * 0.5f, radius);
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box, 0f, crouchTotalHeight * 0.5f, 0f);
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
            this.contactTestBox = new BoxCollisionShape(1f, 1f, 1f);
            this.contactTest = new PhysicsGhostObject(this.contactTestBox);
        }
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

    public float getCurrentHeight() {
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

    public CollisionShape getCurrentCollisionShape() {
        if (isCrouched()) {
            return getCrouchCollisionShape();
        }
        return getCollisionShape();
    }
    
    public PhysicsRigidBody getRigidBody() {
        return this.rigidBody;
    }

    public Vector3fc getPosition() {
        this.rigidBody.getPhysicsLocation(this.positionStore);
        this.jomlPositionStore.set(this.positionStore.x, this.positionStore.y, this.positionStore.z);
        return this.jomlPositionStore;
    }

    public Vector3f getPosition(Vector3f receiver) {
        this.rigidBody.getPhysicsLocation(this.positionStore);
        receiver.set(this.positionStore.x, this.positionStore.y, this.positionStore.z);
        return receiver;
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

    public boolean onGroundOrWillBe() {
        return onGround() || this.pullingToTheGround;
    }

    public Vector3fc getGroundNormal() {
        return groundNormal;
    }

    public float getWalkDirectionX() {
        return this.walkDirectionX * this.walkDirectionSpeed;
    }

    public float getWalkDirectionZ() {
        return this.walkDirectionZ * this.walkDirectionSpeed;
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

    public float getGravityCoefficient() {
        return gravityCoefficient;
    }

    public void setGravityCoefficient(float gravityCoefficient) {
        this.gravityCoefficient = gravityCoefficient;
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
    
    public float getMaxGroundPullDistance() {
        return maxGroundPullDistance;
    }

    public void setMaxGroundPullDistance(float maxGroundPullDistance) {
        this.maxGroundPullDistance = maxGroundPullDistance;
    }

    public float getGroundPullSpeed() {
        return groundPullSpeed;
    }

    public void setGroundPullSpeed(float groundPullSpeed) {
        this.groundPullSpeed = groundPullSpeed;
    }

    public float getGroundPullMaxExternalSpeed() {
        return groundPullMaxExternalSpeed;
    }

    public void setGroundPullMaxExternalSpeed(float groundPullMaxExternalSpeed) {
        this.groundPullMaxExternalSpeed = groundPullMaxExternalSpeed;
    }

    public void jump(float speed) {
        this.nextJumpImpulse += speed;
    }

    public boolean isJumping() {
        return this.jump != 0f;
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

    private Vector3fc rigidBodyVelocity() {
        this.rigidBody.getLinearVelocity(this.velocityGetJme);
        this.velocityGet.set(
                this.velocityGetJme.x,
                this.velocityGetJme.y,
                this.velocityGetJme.z
        );
        return this.velocityGet;
    }

    private void checkNoclipState() {
        if (this.noclipStateChanged) {
            this.rigidBody.setContactResponse(!this.noclipEnabled);
            this.rigidBody.setKinematic(this.noclipEnabled);

            if (this.noclipEnabled) {
                this.rigidBody.setLinearVelocity(com.jme3.math.Vector3f.ZERO);

                this.walkX = 0f;
                this.walkY = 0f;
                this.walkZ = 0f;

                this.jump = 0f;

                this.gravityX = 0f;
                this.gravityY = 0f;
                this.gravityZ = 0f;
            }
        }
    }

    private boolean isGroundNormalInsideThreshold() {
        return this.groundNormal.dot(0f, 1f, 0f) >= this.slopeThreshold;
    }

    private void pullToTheGround(PhysicsSpace space, float timeStep) {
        this.pullingToTheGround = false;
        
        float externalLength = (float) Math.sqrt((this.externalX * this.externalX) + (this.externalY * this.externalY) + (this.externalZ * this.externalZ));
        if (Math.abs(this.jump) > EPSILON
                || Math.abs(this.gravityY) > EPSILON
                || externalLength > this.groundPullMaxExternalSpeed
                || !isGroundNormalInsideThreshold()) {
            this.lastGroundPullHeight = Float.NaN;
            return;
        }

        Vector3fc currentPosition = getPosition();

        com.jme3.math.Vector3f startPosition = this.groundPullStartPosition.set(
                currentPosition.x(),
                currentPosition.y() + (GROUND_PULL_BOX_HEIGHT * 0.5f) + EPSILON,
                currentPosition.z()
        );
        com.jme3.math.Vector3f endPosition = this.groundPullEndPosition.set(
                currentPosition.x(),
                currentPosition.y() + (GROUND_PULL_BOX_HEIGHT * 0.5f) - this.maxGroundPullDistance,
                currentPosition.z()
        );
        this.groundPullStart.setTranslation(startPosition);
        this.groundPullEnd.setTranslation(endPosition);

        this.contactTestBox.setScale(this.contactTestScale.set(
                getRadius() - EPSILON, GROUND_PULL_BOX_HEIGHT * 0.5f, getRadius() - EPSILON
        ));
        List<PhysicsSweepTestResult> results = space.sweepTest(this.contactTestBox, this.groundPullStart, this.groundPullEnd, new ArrayList<>(), 0f);
        results.sort((o1, o2) -> Float.compare(o1.getHitFraction(), o2.getHitFraction()));

        PhysicsSweepTestResult closest = null;
        for (PhysicsSweepTestResult e : results) {
            PhysicsCollisionObject obj = e.getCollisionObject();
            if (obj == null || obj.equals(this.rigidBody) || obj instanceof PhysicsGhostObject) {
                continue;
            }
            closest = e;
            break;
        }

        if (closest == null) {
            this.lastGroundPullHeight = Float.NaN;
            return;
        }

        float newY = (startPosition.y * (1f - closest.getHitFraction())) + (endPosition.y * closest.getHitFraction());
        newY -= GROUND_PULL_BOX_HEIGHT * 0.5f;
        float height = newY - currentPosition.y();

        float instantPull = 0f;
        if (Float.isFinite(this.lastGroundPullHeight) && (this.lastGroundPullHeight - height) > EPSILON) {
            instantPull = this.lastGroundPullHeight;
        }

        this.pullingToTheGround = true;
        this.lastGroundPullHeight = height;

        if (height > -EPSILON) {
            return;
        }

        height += -instantPull;

        if (height > 0f) {
            height = 0f;
        }

        float stepHeight = this.groundPullSpeed * timeStep;
        if (stepHeight < height) {
            stepHeight = height;
        }

        setPosition(
                currentPosition.x(),
                currentPosition.y() + stepHeight + instantPull,
                currentPosition.z()
        );
    }

    private boolean contactTest(PhysicsSpace space, float x, float y, float z, float radius, float height, boolean centered) {
        if (!centered) {
            y += height * 0.5f;
        }
        this.contactTest.setPhysicsLocation(this.contactTestPosition.set(x, y, z));
        this.contactTestBox.setScale(this.contactTestScale.set(radius, height * 0.5f, radius));
        boolean[] collision = {false};
        space.contactTest(this.contactTest, (event) -> {
            PhysicsCollisionObject obj = event.getObjectB();
            if (obj == null) {
                return;
            }
            if (obj.equals(this.contactTest)) {
                obj = event.getObjectA();
            }
            if (obj.equals(this.rigidBody) || obj instanceof PhysicsGhostObject) {
                return;
            }
            collision[0] = true;
        });
        return collision[0];
    }

    private void checkIfShouldCrouch() {
        if (!this.crouchStateChanged) {
            return;
        }
        
        if (!isCrouched()) {
            Vector3fc position = getPosition();
            
            this.rigidBody.setCollisionShape(this.crouchCollisionShape);
            this.crouchStateChanged = false;
            this.crouched = true;

            if (!onGroundOrWillBe()) {
                setPosition(
                        position.x(),
                        position.y() + (this.totalHeight - this.crouchTotalHeight),
                        position.z()
                );
                this.airCrouched = true;
            } else {
                this.airCrouched = false;
            }
        }
    }
    
    private com.jme3.math.Vector3f getGroundNormal(PhysicsSpace space, Vector3fc position, float offsetX, float offsetY, float offsetZ) {
        List<PhysicsRayTestResult> results = space.rayTest(
                this.rayPositionA.set(
                        position.x() + offsetX,
                        position.y() + offsetY + GROUND_NORMAL_RAY_OFFSET,
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

    private void findGroundNormal(PhysicsSpace space) {
        Vector3fc position = getPosition();

        this.groundNormal.set(0f, 1f, 0f);
        if (onGroundOrWillBe()) {
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
            } else {
                this.groundNormal.set(0f, 1f, 0f);
            }
        }
    }

    private void calculateGroundOrientedDirection() {
        this.groundOrientedWalkDirection.set(0f, 0f, 0f);
        if (this.walkDirectionX != 0f && this.walkDirectionZ != 0f) {
            Vector3f walkDir = this.orientedWalk.set(this.walkDirectionX, 0f, this.walkDirectionZ);

            Vector3fc normal = this.groundNormal;
            Vector3f tangent = walkDir.cross(normal, this.orientedTangent).normalize();
            Vector3f bitangent = normal.cross(tangent, this.orientedBitangent).normalize();
            
            this.groundOrientedWalkDirection.set(bitangent);
        }
    }

    private void disableGravityIfNeeded(float timeStep) {
        if (!onGroundOrWillBe() || !isGroundNormalInsideThreshold()) {
            this.gravityGroundCounter = 0f;
            this.internalGravityMultiplier = this.gravityCoefficient;
            return;
        }
        this.gravityGroundCounter += timeStep;
        if (this.gravityGroundCounter >= GRAVITY_CUTOFF_TIME) {
            this.gravityGroundCounter = GRAVITY_CUTOFF_TIME;
            this.internalGravityMultiplier = 0f;
        }
    }

    private void applyVelocity(float x, float y, float z) {
        float mass = this.rigidBody.getMass();
        this.velocityApply.set(x * mass, y * mass, z * mass);
        this.rigidBody.applyCentralImpulse(this.velocityApply);
    }

    private void applyWalk(float timeStep) {
        float movementRoughness = (onGroundOrWillBe() ? getGroundMovementRoughness() : getAirMovementRoughness());

        float targetX = this.groundOrientedWalkDirection.x() * this.walkDirectionSpeed;
        float targetY = this.groundOrientedWalkDirection.y() * this.walkDirectionSpeed;
        float targetZ = this.groundOrientedWalkDirection.z() * this.walkDirectionSpeed;

        float dX = targetX - this.walkX;
        float dY = targetY - this.walkY;
        float dZ = targetZ - this.walkZ;

        float dXStep = dX * timeStep * movementRoughness;
        float dYStep = dY * timeStep * movementRoughness;
        float dZStep = dZ * timeStep * movementRoughness;

        if (Math.abs(dXStep) > Math.abs(dX) || Math.abs(dX) < EPSILON) {
            dXStep = dX;
        }
        if (Math.abs(dYStep) > Math.abs(dY) || Math.abs(dY) < EPSILON) {
            dYStep = dY;
        }
        if (Math.abs(dZStep) > Math.abs(dZ) || Math.abs(dZ) < EPSILON) {
            dZStep = dZ;
        }

        this.appliedWalkX = dXStep;
        this.appliedWalkY = dYStep;
        this.appliedWalkZ = dZStep;

        applyVelocity(dXStep, dYStep, dZStep);
    }

    private void applyJump() {
        applyVelocity(0f, this.nextJumpImpulse, 0f);
        this.appliedJump = this.nextJumpImpulse;
        this.nextJumpImpulse = 0f;
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

    private void applyGravity(PhysicsSpace space, float timeStep) {
        Vector3fc gravity = spaceGravity(space);

        float vX = gravity.x() * timeStep * this.internalGravityMultiplier;
        float vY = gravity.y() * timeStep * this.internalGravityMultiplier;
        float vZ = gravity.z() * timeStep * this.internalGravityMultiplier;

        applyVelocity(vX, vY, vZ);

        this.appliedGravityX = vX;
        this.appliedGravityY = vY;
        this.appliedGravityZ = vZ;
    }

    private float normalize(float value, float totalSum) {
        if (Math.abs(totalSum) < EPSILON) {
            return 0f;
        }
        return value /= totalSum;
    }

    private void calculateTotalVelocities() {
        this.appliedTotalX = this.appliedWalkX + this.appliedGravityX;
        this.appliedTotalY = this.appliedWalkY + this.appliedGravityY + this.appliedJump;
        this.appliedTotalZ = this.appliedWalkZ + this.appliedGravityZ;

        this.appliedWalkX = normalize(this.appliedWalkX, this.appliedTotalX);
        this.appliedWalkY = normalize(this.appliedWalkY, this.appliedTotalY);
        this.appliedWalkZ = normalize(this.appliedWalkZ, this.appliedTotalZ);

        this.appliedJump = normalize(this.appliedJump, this.appliedTotalY);

        this.appliedGravityX = normalize(this.appliedGravityX, this.appliedTotalX);
        this.appliedGravityY = normalize(this.appliedGravityY, this.appliedTotalY);
        this.appliedGravityZ = normalize(this.appliedGravityZ, this.appliedTotalZ);

        this.appliedWalkX = Math.max(this.appliedWalkX, 0f);
        this.appliedWalkY = Math.max(this.appliedWalkY, 0f);
        this.appliedWalkZ = Math.max(this.appliedWalkZ, 0f);

        this.appliedJump = Math.max(this.appliedJump, 0f);

        this.appliedGravityX = Math.max(this.appliedGravityX, 0f);
        this.appliedGravityY = Math.max(this.appliedGravityY, 0f);
        this.appliedGravityZ = Math.max(this.appliedGravityZ, 0f);

        float sumX = this.appliedWalkX + this.appliedGravityX;
        float sumY = this.appliedWalkY + this.appliedJump + this.appliedGravityY;
        float sumZ = this.appliedWalkZ + this.appliedGravityZ;

        this.appliedWalkX = normalize(this.appliedWalkX, sumX);
        this.appliedWalkY = normalize(this.appliedWalkY, sumY);
        this.appliedWalkZ = normalize(this.appliedWalkZ, sumZ);

        this.appliedJump = normalize(this.appliedJump, sumY);

        this.appliedGravityX = normalize(this.appliedGravityX, sumX);
        this.appliedGravityY = normalize(this.appliedGravityY, sumY);
        this.appliedGravityZ = normalize(this.appliedGravityZ, sumZ);

        Vector3fc velocity = rigidBodyVelocity();

        this.deltaX = velocity.x();
        this.deltaY = velocity.y();
        this.deltaZ = velocity.z();
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        checkNoclipState();
        
        if (this.noclipEnabled) {
            return;
        }
        
        pullToTheGround(space, timeStep);
        checkIfShouldCrouch();
        
        findGroundNormal(space);
        calculateGroundOrientedDirection();

        disableGravityIfNeeded(timeStep);

        applyWalk(timeStep);
        applyJump();
        applyGravity(space, timeStep);

        calculateTotalVelocities();
    }
    
    private void checkIfShouldUncrouch(PhysicsSpace space) {
        if (!this.crouchStateChanged) {
            return;
        }

        if (isCrouched()) {
            Vector3fc position = getPosition();

            boolean canUncrouch = !contactTest(space,
                    position.x(), position.y() + EPSILON, position.z(),
                    getRadius() - UNCROUCH_TEST_RADIUS_NEGATIVE_MARGIN, getTotalHeight(),
                    false
            );
            if (canUncrouch) {
                this.rigidBody.setCollisionShape(this.collisionShape);
                boolean canAirUncrouch = !contactTest(space,
                        position.x(), position.y() - EPSILON - (this.totalHeight - this.crouchTotalHeight), position.z(),
                        getRadius() - UNCROUCH_TEST_RADIUS_NEGATIVE_MARGIN, getTotalHeight(),
                        false
                );
                if (this.airCrouched && canAirUncrouch) {
                    setPosition(
                            position.x(),
                            position.y() - (this.totalHeight - this.crouchTotalHeight),
                            position.z()
                    );
                }
                this.crouchStateChanged = false;
                this.crouched = false;
                this.airCrouched = false;
            }
        }
    }
    
    private void checkIfOnGround(PhysicsSpace space) {
        Vector3fc position = getPosition();
        this.onGround = contactTest(space,
                position.x(), position.y(), position.z(),
                getRadius() - GROUND_TEST_RADIUS_NEGATIVE_MARGIN, GROUND_TEST_HEIGHT * 2f,
                true
        );
    }

    private float clamp(float velocity, float max) {
        if (Math.signum(velocity) != Math.signum(max)) {
            velocity = 0f;
        } else if (Math.abs(velocity) > Math.abs(max)) {
            velocity = max;
        }
        return velocity;
    }

    private void collectAppliedVelocities() {
        Vector3fc velocity = rigidBodyVelocity();
        this.deltaX -= velocity.x();
        this.deltaY -= velocity.y();
        this.deltaZ -= velocity.z();

        this.appliedTotalX = clamp(this.appliedTotalX + this.deltaX, this.appliedTotalX);
        this.appliedTotalY = clamp(this.appliedTotalY + this.deltaY, this.appliedTotalY);
        this.appliedTotalZ = clamp(this.appliedTotalZ + this.deltaZ, this.appliedTotalZ);

        this.appliedWalkX *= this.appliedTotalX;
        this.appliedWalkY *= this.appliedTotalY;
        this.appliedWalkZ *= this.appliedTotalZ;

        this.appliedJump *= this.appliedTotalY;

        this.appliedGravityX *= this.appliedTotalX;
        this.appliedGravityY *= this.appliedTotalY;
        this.appliedGravityZ *= this.appliedTotalZ;

        this.walkX += this.appliedWalkX;
        this.walkY += this.appliedWalkY;
        this.walkZ += this.appliedWalkZ;

        this.jump += this.appliedJump;

        this.gravityX += this.appliedGravityX;
        this.gravityY += this.appliedGravityY;
        this.gravityZ += this.appliedGravityZ;

        this.internalX = this.walkX + this.gravityX;
        this.internalY = this.walkY + this.jump + this.gravityY;
        this.internalZ = this.walkZ + this.gravityZ;

        this.walkX = normalize(this.walkX, this.internalX);
        this.walkY = normalize(this.walkY, this.internalY);
        this.walkZ = normalize(this.walkZ, this.internalZ);

        this.jump = normalize(this.jump, this.internalY);

        this.gravityX = normalize(this.gravityX, this.internalX);
        this.gravityY = normalize(this.gravityY, this.internalY);
        this.gravityZ = normalize(this.gravityZ, this.internalZ);

        this.walkX = Math.max(this.walkX, 0f);
        this.walkY = Math.max(this.walkY, 0f);
        this.walkZ = Math.max(this.walkZ, 0f);

        this.jump = Math.max(this.jump, 0f);

        this.gravityX = Math.max(this.gravityX, 0f);
        this.gravityY = Math.max(this.gravityY, 0f);
        this.gravityZ = Math.max(this.gravityZ, 0f);

        this.internalX = clamp(this.internalX, velocity.x());
        this.internalY = clamp(this.internalY, velocity.y());
        this.internalZ = clamp(this.internalZ, velocity.z());

        this.walkX *= this.internalX;
        this.walkY *= this.internalY;
        this.walkZ *= this.internalZ;

        this.jump *= this.internalY;

        this.gravityX *= this.internalX;
        this.gravityY *= this.internalY;
        this.gravityZ *= this.internalZ;

        this.externalX = velocity.x() - this.internalX;
        this.externalY = velocity.y() - this.internalY;
        this.externalZ = velocity.z() - this.internalZ;
    }

    private void applyFriction(float timestep) {
        float friction = (onGround() ? getGroundFriction() : getAirFriction());

        float dX = this.externalX;
        float dY = this.externalY;
        float dZ = this.externalZ;

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

        applyVelocity(fX, fY, fZ);

        this.externalX += fX;
        this.externalY += fY;
        this.externalZ += fZ;
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        if (this.noclipEnabled) {
            return;
        }
        
        checkIfShouldUncrouch(space);
        checkIfOnGround(space);
        
        collectAppliedVelocities();
        applyFriction(timeStep);
    }

}
