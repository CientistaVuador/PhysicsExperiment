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
 * this is a nightmare
 *
 * @author Cien
 */
public class CharacterController implements PhysicsTickListener {

    public static final float EPSILON = 0.00001f;

    public static final float GROUND_PULL_EXTERNAL_Y_VELOCITY_THRESHOLD = 2f;
    public static final float CLIMB_SLOPE_EPSILON = 0.0001f;
    public static final float CLIMB_SLOPE_EXTRA_HEIGHT = 0.04f;
    public static final int CLIMB_SLOPE_MAX_PREVIEW_FRAMES = 16;

    public static final float UNCROUCH_TEST_OFFSET = 0.04f;
    public static final float GROUND_TEST_OFFSET = 0.075f;
    public static final float GROUND_NORMAL_RAY_OFFSET = 1f;
    public static final float GRAVITY_CUTOFF_TIME = 0.1f;

    public static final float WALL_NORMAL_CHECK_CONFIDENCE = 0.75f;
    public static final float WALL_NORMAL_CHECK_Y_OFFSET = 0.01f;
    public static final float WALL_NORMAL_CHECK_EPSILON = 0.001f;

    private static final Vector3f[] GROUND_NORMAL_OFFSETS = new Vector3f[]{
        new Vector3f(0f, 0f, 0f),
        new Vector3f(1f, 0f, 0f),
        new Vector3f(-1f, 0f, 0f),
        new Vector3f(0f, 0f, 1f),
        new Vector3f(0f, 0f, -1f)
    };

    public static final float[] WALL_NORMAL_OFFSETS = new float[]{
        -1f, -0.5f, 0f, 0.5f, 1f
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

    private final BoxCollisionShape collisionShapeRaw;
    private final BoxCollisionShape crouchCollisionShapeRaw;

    private final PhysicsRigidBody rigidBody;
    private final PhysicsGhostObject groundTest;

    private final PhysicsGhostObject uncrouchTest;
    private final PhysicsGhostObject airUncrouchTest;

    private final PhysicsGhostObject climbSlopeTest;

    //character state
    private boolean noclipEnabled = false;
    private boolean noclipStateChanged = false;

    private boolean crouched = false;
    private boolean crouchStateChanged = false;
    private boolean airCrouched = false;

    private boolean onGround = false;
    private boolean canUncrouch = true;
    private boolean canAirUncrouch = false;
    private boolean pullingToTheGround = false;

    private final Vector3f groundNormal = new Vector3f();
    private final Vector3f groundOrientedWalkDirection = new Vector3f();

    private boolean climblingSlope = false;
    private int climbSlopeFrame = 0;
    private float climbSlopeHeight = 0f;

    //character movement configuration
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;
    private float walkDirectionSpeed = 0f;

    private float gravityMultiplier = 2.5f;

    private float airMovementRoughness = 3f;
    private float groundMovementRoughness = 10f;

    private float slopeThreshold = 0.75f;

    private float airFriction = 1f;
    private float groundFriction = 6f;

    private float maxGroundPullDistance = 0.60f;
    private float groundPullTime = 0.0375f;

    private float maxSlopeClimbDistance = 0.60f;
    private float slopeClimbTime = 0.05f;

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
    private float internalGravityMultiplier = this.gravityMultiplier;
    private float gravityGroundCounter = 0f;

    //recycled objects
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

    public CharacterController(float radius, float totalHeight, float crouchTotalHeight, float mass) {
        this.totalHeight = totalHeight;
        this.crouchTotalHeight = crouchTotalHeight;
        this.radius = radius;

        {
            BoxCollisionShape box = new BoxCollisionShape(radius, totalHeight * 0.5f, radius);
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box, 0f, totalHeight * 0.5f, 0f);
            this.collisionShape = compound;
            this.collisionShapeRaw = box;
        }

        {
            BoxCollisionShape box = new BoxCollisionShape(radius, crouchTotalHeight * 0.5f, radius);
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box, 0f, crouchTotalHeight * 0.5f, 0f);
            this.crouchCollisionShape = compound;
            this.crouchCollisionShapeRaw = box;
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
            BoxCollisionShape box = new BoxCollisionShape(radius * 0.95f, GROUND_TEST_OFFSET, radius * 0.95f);
            this.groundTest = new PhysicsGhostObject(box);
            this.groundTest.addToIgnoreList(this.rigidBody);
        }
        this.uncrouchTest = new PhysicsGhostObject(this.collisionShape);
        this.uncrouchTest.addToIgnoreList(this.rigidBody);

        this.airUncrouchTest = new PhysicsGhostObject(this.collisionShape);
        this.airUncrouchTest.addToIgnoreList(this.rigidBody);

        this.groundTest.addToIgnoreList(this.uncrouchTest);
        this.groundTest.addToIgnoreList(this.airUncrouchTest);

        this.uncrouchTest.addToIgnoreList(this.groundTest);
        this.uncrouchTest.addToIgnoreList(this.airUncrouchTest);

        this.airUncrouchTest.addToIgnoreList(this.groundTest);
        this.airUncrouchTest.addToIgnoreList(this.uncrouchTest);

        this.climbSlopeTest = new PhysicsGhostObject(this.collisionShape);

        this.climbSlopeTest.addToIgnoreList(this.rigidBody);
        this.climbSlopeTest.addToIgnoreList(this.groundTest);
        this.climbSlopeTest.addToIgnoreList(this.uncrouchTest);
        this.climbSlopeTest.addToIgnoreList(this.airUncrouchTest);
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addTickListener(this);
        this.space.addCollisionObject(this.rigidBody);
        this.space.addCollisionObject(this.groundTest);
        this.space.addCollisionObject(this.uncrouchTest);
        this.space.addCollisionObject(this.airUncrouchTest);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeTickListener(this);
        this.space.removeCollisionObject(this.rigidBody);
        this.space.removeCollisionObject(this.groundTest);
        this.space.removeCollisionObject(this.uncrouchTest);
        this.space.removeCollisionObject(this.airUncrouchTest);
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

    public CollisionShape getCurrentCollisionShape() {
        if (isCrouched()) {
            return getCrouchCollisionShape();
        }
        return getCollisionShape();
    }

    public BoxCollisionShape getRawCollisionShape() {
        return collisionShapeRaw;
    }

    public BoxCollisionShape getRawCrouchCollisionShape() {
        return crouchCollisionShapeRaw;
    }

    public BoxCollisionShape getRawCurrentCollisionShape() {
        if (isCrouched()) {
            return getRawCrouchCollisionShape();
        }
        return getRawCollisionShape();
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

    public boolean isAirCrouched() {
        return airCrouched;
    }

    public void setCrouched(boolean crouched) {
        this.crouchStateChanged = (this.crouched != crouched);
    }

    public boolean onGround() {
        return this.onGround;
    }

    public boolean canUncrouch() {
        return this.canUncrouch;
    }

    public boolean isPullingToTheGround() {
        return pullingToTheGround;
    }

    public boolean onGroundOrWillBe() {
        return onGround() || isPullingToTheGround();
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

    public float getMaxGroundPullDistance() {
        return maxGroundPullDistance;
    }

    public void setMaxGroundPullDistance(float maxGroundPullDistance) {
        this.maxGroundPullDistance = maxGroundPullDistance;
    }

    public float getGroundPullTime() {
        return groundPullTime;
    }

    public void setGroundPullTime(float groundPullTime) {
        this.groundPullTime = groundPullTime;
    }

    //public float getMaxSlopeClimbDistance() {
    //    return maxSlopeClimbDistance;
    //}
    //public void setMaxSlopeClimbDistance(float maxSlopeClimbDistance) {
    //    this.maxSlopeClimbDistance = maxSlopeClimbDistance;
    //}
    //public float getSlopeClimbTime() {
    //    return slopeClimbTime;
    //}
    //public void setSlopeClimbTime(float slopeClimbTime) {
    //    this.slopeClimbTime = slopeClimbTime;
    //}
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
        int stack = 0;

        this.pullingToTheGround = false;

        if (Math.abs(this.jump) > EPSILON
                || Math.abs(this.gravityY) > EPSILON
                || Math.abs(this.externalY) > GROUND_PULL_EXTERNAL_Y_VELOCITY_THRESHOLD
                || !isGroundNormalInsideThreshold()) {
            return;
        }

        //if (this.climblingSlope || this.climbSlopeFrame > 0) {
        //    return;
        //}
        Vector3fc currentPosition = getPosition();

        com.jme3.math.Vector3f startPosition = this.vectorsStack[stack++].set(currentPosition.x(), currentPosition.y() + (getHeight() * 0.5f), currentPosition.z());
        com.jme3.math.Vector3f endPosition = this.vectorsStack[stack++].set(currentPosition.x(), (currentPosition.y() + (getHeight() * 0.5f)) - this.maxGroundPullDistance, currentPosition.z());
        this.groundPullStart.setTranslation(startPosition);
        this.groundPullEnd.setTranslation(endPosition);

        List<PhysicsSweepTestResult> results = space.sweepTest(getRawCurrentCollisionShape(), this.groundPullStart, this.groundPullEnd, new ArrayList<>(), 0f);
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
            return;
        }

        float newY = (startPosition.y * (1f - closest.getHitFraction())) + (endPosition.y * closest.getHitFraction());
        newY -= getHeight() * 0.5f;

        float height = newY - currentPosition.y();

        if (Math.abs(height) < EPSILON) {
            return;
        }

        this.pullingToTheGround = true;

        float speed = height / this.groundPullTime;
        if (timeStep > this.groundPullTime) {
            speed = height / timeStep;
        }

        setPosition(
                currentPosition.x(),
                currentPosition.y() + (speed * timeStep),
                currentPosition.z()
        );
    }

    private PhysicsCollisionObject findClimbSlopeHeight(PhysicsSpace space, float timeStep, int frame) {
        Vector3fc currentPosition = getPosition();
        Vector3fc currentVelocity = rigidBodyVelocity();

        float xOffset = currentVelocity.x() * timeStep * frame;
        float yOffset = currentVelocity.y() * timeStep * frame;
        float zOffset = currentVelocity.z() * timeStep * frame;

        this.climbSlopeTest.setPhysicsLocation(new com.jme3.math.Vector3f(
                currentPosition.x() + xOffset,
                currentPosition.y() + yOffset,
                currentPosition.z() + zOffset
        ));

        if (space.contactTest(this.climbSlopeTest, null) == 0) {
            return null;
        }

        com.jme3.math.Vector3f startPosition = new com.jme3.math.Vector3f(
                currentPosition.x() + xOffset,
                currentPosition.y() + (getHeight() * 0.5f) + this.maxSlopeClimbDistance,
                currentPosition.z() + zOffset
        );
        com.jme3.math.Vector3f finalPosition = new com.jme3.math.Vector3f(
                currentPosition.x() + xOffset,
                currentPosition.y() + (getHeight() * 0.5f),
                currentPosition.z() + zOffset
        );
        Transform start = new Transform().setTranslation(startPosition);
        Transform end = new Transform().setTranslation(finalPosition);

        BoxCollisionShape currentRawShape;
        if (isCrouched()) {
            currentRawShape = this.crouchCollisionShapeRaw;
        } else {
            currentRawShape = this.collisionShapeRaw;
        }

        List<PhysicsSweepTestResult> results = space.sweepTest(currentRawShape, start, end, new ArrayList<>(), 0f);
        results.sort((o1, o2) -> Float.compare(o1.getHitFraction(), o2.getHitFraction()));

        CollisionShape currentShape;
        if (isCrouched()) {
            currentShape = this.collisionShape;
        } else {
            currentShape = this.crouchCollisionShape;
        }
        this.climbSlopeTest.setCollisionShape(currentShape);

        PhysicsSweepTestResult closest = null;
        float height = 0f;
        for (PhysicsSweepTestResult e : results) {
            PhysicsCollisionObject obj = e.getCollisionObject();
            if (obj == null || obj.equals(this.rigidBody) || obj instanceof PhysicsGhostObject) {
                continue;
            }

            float newY = (startPosition.y * (1f - e.getHitFraction())) + (finalPosition.y * e.getHitFraction());
            newY -= getHeight() * 0.5f;
            height = newY - currentPosition.y();
            height += CLIMB_SLOPE_EXTRA_HEIGHT;

            this.climbSlopeTest.setPhysicsLocation(new com.jme3.math.Vector3f(
                    currentPosition.x() + xOffset,
                    currentPosition.y() + height,
                    currentPosition.z() + zOffset
            ));

            if (space.contactTest(this.climbSlopeTest, null) == 0) {
                closest = e;
                break;
            }
        }

        if (closest == null) {
            return null;
        }

        if (Math.abs(height) < CLIMB_SLOPE_EPSILON) {
            return null;
        }

        this.climbSlopeHeight = height;
        return closest.getCollisionObject();
    }

    private int isWallNormalValid(PhysicsSpace space, com.jme3.math.Vector3f start, com.jme3.math.Vector3f end) {
        List<PhysicsRayTestResult> results = space.rayTest(start, end);

        if (results.isEmpty()) {
            return -1;
        }

        for (PhysicsRayTestResult e : results) {
            PhysicsCollisionObject o = e.getCollisionObject();
            if (o == null || o.equals(this.rigidBody) || o instanceof PhysicsGhostObject) {
                continue;
            }
            return (Math.abs(e.getHitNormalLocal(null).y) < WALL_NORMAL_CHECK_EPSILON ? 1 : 0);
        }

        return -1;
    }

    private boolean isValidSlope(PhysicsSpace space) {
        Vector3f walkDir = this.groundOrientedWalkDirection;
        Vector3f right = this.groundOrientedWalkDirection.cross(this.groundNormal, new Vector3f()).normalize();

        Vector3fc position = getPosition();

        com.jme3.math.Vector3f from = new com.jme3.math.Vector3f();
        com.jme3.math.Vector3f to = new com.jme3.math.Vector3f();

        float confidence = 0f;
        int count = 0;
        for (int i = 0; i < WALL_NORMAL_OFFSETS.length; i++) {
            float offset = WALL_NORMAL_OFFSETS[i] * getRadius();

            from.set(
                    position.x() + (right.x() * offset),
                    position.y() + (right.y() * offset) + WALL_NORMAL_CHECK_Y_OFFSET,
                    position.z() + (right.z() * offset)
            );
            to.set(
                    position.x() + (right.x() * offset) + (walkDir.x() * getRadius() * 3f),
                    position.y() + (right.y() * offset) + (walkDir.y() * getRadius() * 3f) + WALL_NORMAL_CHECK_Y_OFFSET,
                    position.z() + (right.z() * offset) + (walkDir.z() * getRadius() * 3f)
            );
            int result = isWallNormalValid(space, from, to);
            if (result == -1) {
                continue;
            }
            if (result != 0) {
                confidence++;
                count++;
            }
        }
        if (count == 0) {
            return false;
        }
        confidence /= count;

        return confidence >= WALL_NORMAL_CHECK_CONFIDENCE;
    }

    private void climbSlope(PhysicsSpace space, float timeStep) {
        this.climblingSlope = false;

        if (this.climbSlopeFrame > 0) {
            return;
        }

        float walkSquaredLength = (this.walkX * this.walkX) + (this.walkY * this.walkY) + (this.walkZ * this.walkZ);
        if (walkSquaredLength < (EPSILON * EPSILON)) {
            return;
        }

        if (Math.abs(this.jump) > EPSILON
                || Math.abs(this.gravityY) > EPSILON
                || Math.abs(this.externalY) > GROUND_PULL_EXTERNAL_Y_VELOCITY_THRESHOLD) {
            return;
        }

        if (!isValidSlope(space)) {
            return;
        }

        Vector3fc currentPosition = getPosition();

        PhysicsCollisionObject found = null;
        for (int i = 0; i < CLIMB_SLOPE_MAX_PREVIEW_FRAMES; i++) {
            found = findClimbSlopeHeight(space, timeStep, i);
            if (found != null) {
                this.climbSlopeFrame = i;
                break;
            }
        }

        if (found == null) {
            return;
        }

        if (!found.isStatic()) {
            return;
        }

        setPosition(
                currentPosition.x(),
                currentPosition.y() + this.climbSlopeHeight,
                currentPosition.z()
        );

        this.climblingSlope = true;
    }

    private void checkIfShouldCrouch() {
        if (!this.crouchStateChanged) {
            return;
        }

        if (isCrouched()) {
            if (this.canUncrouch) {
                this.rigidBody.setCollisionShape(this.collisionShape);
                this.crouchStateChanged = false;
                this.crouched = false;
                this.airCrouched = false;

                if (this.airCrouched && this.canAirUncrouch) {
                    Vector3fc position = getPosition();
                    this.rigidBody.setPhysicsLocation(this.vectorsStack[0].set(
                            position.x(),
                            position.y() - (this.totalHeight - this.crouchTotalHeight),
                            position.z()
                    ));
                }
            }
        } else {
            this.rigidBody.setCollisionShape(this.crouchCollisionShape);
            this.crouchStateChanged = false;
            this.crouched = true;

            if (!onGround()) {
                Vector3fc position = getPosition();
                this.rigidBody.setPhysicsLocation(this.vectorsStack[0].set(
                        position.x(),
                        position.y() + (this.totalHeight - this.crouchTotalHeight),
                        position.z()
                ));
                this.airCrouched = true;
            } else {
                this.airCrouched = false;
            }
        }
    }

    private void setGhostPositions() {
        Vector3fc position = getPosition();

        int stack = 0;

        this.groundTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(), position.y(), position.z()
        ));

        this.uncrouchTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(), position.y() + UNCROUCH_TEST_OFFSET, position.z()
        ));

        this.airUncrouchTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(), (position.y() - UNCROUCH_TEST_OFFSET) - (this.totalHeight - this.crouchTotalHeight), position.z()
        ));
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
        if (!onGroundOrWillBe() || !isGroundNormalInsideThreshold()) {
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

    private void decreaseClimbSlopeFrames() {
        this.climbSlopeFrame--;
        if (this.climbSlopeFrame < 0) {
            this.climbSlopeFrame = 0;
        }
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        checkNoclipState();

        if (this.noclipEnabled) {
            return;
        }

        //decreaseClimbSlopeFrames();
        pullToTheGround(space, timeStep);
        checkIfShouldCrouch();

        setGhostPositions();
        findGroundNormal(space);
        calculateGroundOrientedDirection();

        disableGravityIfNeeded(timeStep);

        applyWalk(timeStep);
        applyJump();
        applyGravity(space, timeStep);

        calculateTotalVelocities();

        //climbSlope(space, timeStep);
        //if (this.climblingSlope) {
        //    setGhostPositions();
        //    findGroundNormal(space);
        //    calculateGroundOrientedDirection();
        //}
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

        this.onGround = checkGhostCollision(space, this.groundTest);
        this.canUncrouch = !checkGhostCollision(space, this.uncrouchTest);
        this.canAirUncrouch = !checkGhostCollision(space, this.airUncrouchTest);

        collectAppliedVelocities();
        applyFriction(timeStep);
    }

}
