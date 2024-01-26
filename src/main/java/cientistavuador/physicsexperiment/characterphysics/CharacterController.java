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
public class CharacterController implements PhysicsTickListener {

    public static final float GROUND_CEILING_MARGIN = 0.05f;

    private PhysicsSpace space = null;
    
    //character
    private final float totalHeight;
    private final float crouchTotalHeight;
    private final float radius;
    private final float mass;
    
    //character collision
    private final CollisionShape collisionShape;
    private final CollisionShape crouchCollisionShape;

    private final PhysicsRigidBody rigidBody;

    private final PhysicsGhostObject uncrouchTest;
    private final PhysicsGhostObject groundTest;
    private final PhysicsGhostObject ceilingTest;

    //gravity
    private float gravity = -9.8f;

    //movement
    private float walkX = 0f;
    private float walkZ = 0f;
    private float fallSpeed = 0f;
    private float jumpSpeed = 0f;

    //status
    private boolean noclipEnabled = false;
    private boolean onGround = false;
    private boolean onCeiling = false;
    private boolean canUncrouch = false;
    private boolean crouched = false;

    //internal
    private float wX;
    private float wY;
    private float wZ;

    private boolean changeCrouchState = false;
    private boolean noclipStateChanged = false;
    private final Vector3f groundNormal = new Vector3f(0f, 1f, 0f);

    //vector stacks
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

    public CharacterController(float radius, float totalHeight, float crouchTotalHeight, float mass) {
        this.totalHeight = totalHeight;
        this.crouchTotalHeight = crouchTotalHeight;
        this.radius = radius;
        this.mass = mass;

        {
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(
                    radius,
                    totalHeight - (radius * 2f)
            );
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, totalHeight / 2f, 0f);
            this.collisionShape = compound;
        }

        {
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(
                    radius,
                    crouchTotalHeight - (radius * 2f)
            );
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, crouchTotalHeight / 2f, 0f);
            this.crouchCollisionShape = compound;
        }

        this.rigidBody = new PhysicsRigidBody(this.collisionShape, mass);
        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setEnableSleep(false);
        this.rigidBody.setProtectGravity(true);
        this.rigidBody.setGravity(com.jme3.math.Vector3f.ZERO);

        {
            float rad = radius * 0.98f;
            float hei = totalHeight * 0.98f;
            CapsuleCollisionShape capsule = new CapsuleCollisionShape(
                    rad,
                    hei - (rad * 2f)
            );
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(capsule, 0f, totalHeight / 2f, 0f);
            this.uncrouchTest = new PhysicsGhostObject(compound);
        }

        float shapeFactor = 0.95f;
        SphereCollisionShape sphere = new SphereCollisionShape(radius * shapeFactor);
        {
            CompoundCollisionShape sphereCompound = new CompoundCollisionShape(1);
            sphereCompound.addChildShape(sphere, 0f, radius * shapeFactor, 0f);
            this.groundTest = new PhysicsGhostObject(sphereCompound);
        }
        {
            CompoundCollisionShape sphereCompound = new CompoundCollisionShape(1);
            sphereCompound.addChildShape(sphere, 0f, -(radius * shapeFactor), 0f);
            this.ceilingTest = new PhysicsGhostObject(sphereCompound);
        }
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addCollisionObject(this.rigidBody);
        this.space.addCollisionObject(this.groundTest);
        this.space.addCollisionObject(this.ceilingTest);
        this.space.addCollisionObject(this.uncrouchTest);
        this.space.addTickListener(this);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeCollisionObject(this.rigidBody);
        this.space.removeCollisionObject(this.groundTest);
        this.space.removeCollisionObject(this.ceilingTest);
        this.space.removeCollisionObject(this.uncrouchTest);
        this.space.removeTickListener(this);
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

    public float getMass() {
        return mass;
    }

    public CollisionShape getCollisionShape() {
        return collisionShape;
    }

    public CollisionShape getCrouchCollisionShape() {
        return crouchCollisionShape;
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
        this.noclipStateChanged = true;
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

    public boolean onCeiling() {
        return this.onCeiling;
    }

    public boolean isCrouched() {
        return crouched;
    }

    public void setCrouched(boolean crouched) {
        this.changeCrouchState = (this.crouched != crouched);
    }

    public boolean isJumping() {
        return this.jumpSpeed != 0f;
    }
    
    public void jump(float speed) {
        this.jumpSpeed += speed;
    }

    private void updateCrouchState() {
        if (this.changeCrouchState) {
            if (this.crouched && this.canUncrouch) {
                this.rigidBody.setCollisionShape(this.collisionShape);
                this.crouched = false;
                this.changeCrouchState = false;
            } else if (!this.crouched) {
                this.rigidBody.setCollisionShape(this.crouchCollisionShape);
                this.crouched = true;
                this.changeCrouchState = false;
            }
        }
    }
    
    private void updateGhostsPositions(Vector3fc position) {
        int stack = 0;
        
        this.uncrouchTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(),
                position.y(),
                position.z()
        ));
        
        this.groundTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(),
                position.y() - GROUND_CEILING_MARGIN,
                position.z()
        ));

        this.ceilingTest.setPhysicsLocation(this.vectorsStack[stack++].set(
                position.x(),
                position.y() + getHeight() + GROUND_CEILING_MARGIN,
                position.z()
        ));
    }

    private void updateGroundNormal(Vector3fc position) {
        int stack = 0;

        if (onGround()) {
            List<PhysicsRayTestResult> results = space.rayTest(
                    this.vectorsStack[stack++].set(position.x(), position.y(), position.z()),
                    this.vectorsStack[stack++].set(position.x(), position.y() - 1f, position.z())
            );
            this.groundNormal.set(0f, 1f, 0f);
            for (PhysicsRayTestResult o : results) {
                if (o.getCollisionObject().equals(this.rigidBody) || o.getCollisionObject() instanceof PhysicsGhostObject) {
                    continue;
                }
                com.jme3.math.Vector3f n = o.getHitNormalLocal(this.vectorsStack[stack++]);
                this.groundNormal.set(n.x, n.y, n.z);
                break;
            }
            if (this.groundNormal.dot(0f, 1f, 0f) < 0.75f) {
                this.groundNormal.set(0f, 1f, 0f);
            }
        }
    }

    private void updateFallAndJumpSpeeds(float timeStep) {
        this.fallSpeed += this.gravity * timeStep;
        if (onGround()) {
            this.fallSpeed = 0f;
        }

        this.jumpSpeed += this.gravity * timeStep;
        if (this.jumpSpeed < 0f || onCeiling()) {
            this.jumpSpeed = 0f;
        }
    }

    private void calculateWalkDirection() {
        int stack = 0;

        this.wX = this.walkX;
        this.wY = 0f;
        this.wZ = this.walkZ;

        if (onGround() && this.walkX != 0f && this.walkZ != 0f) {
            Vector3f walkDir = this.vectorsJomlStack[stack++].set(this.walkX, 0f, this.walkZ);
            float walkSpeed = walkDir.length();
            walkDir.normalize();

            Vector3fc normal = this.groundNormal;
            Vector3f tangent = normal.cross(walkDir.negate(), this.vectorsJomlStack[stack++]).normalize();
            Vector3f bitangent = normal.cross(tangent, this.vectorsJomlStack[stack++]).normalize();

            bitangent.mul(walkSpeed);

            this.wX = bitangent.x();
            this.wY = bitangent.y();
            this.wZ = bitangent.z();
        }
    }

    private void setCharacterSpeed() {
        int stack = 0;

        com.jme3.math.Vector3f speed = this.vectorsStack[stack++].set(this.wX,
                this.wY + this.fallSpeed + this.jumpSpeed,
                this.wZ
        );

        if (this.noclipStateChanged) {
            this.noclipStateChanged = false;
            if (this.noclipEnabled) {
                this.rigidBody.setContactResponse(false);
            } else {
                this.rigidBody.setContactResponse(true);
            }
        }

        if (this.noclipEnabled) {
            this.fallSpeed = 0f;
            this.jumpSpeed = 0f;
            this.rigidBody.setLinearVelocity(com.jme3.math.Vector3f.ZERO);
        } else {
            this.rigidBody.setLinearVelocity(speed);
        }
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        Vector3fc position = getPosition();

        updateCrouchState();
        updateGhostsPositions(position);
        updateGroundNormal(position);
        updateFallAndJumpSpeeds(timeStep);
        calculateWalkDirection();
        setCharacterSpeed();
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

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        this.onGround = checkGhostCollision(space, this.groundTest);
        this.onCeiling = checkGhostCollision(space, this.ceilingTest);
        this.canUncrouch = !checkGhostCollision(space, this.uncrouchTest);
    }

}
