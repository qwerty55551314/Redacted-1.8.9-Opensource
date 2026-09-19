package celeritas;

import java.nio.FloatBuffer;

import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Builds the {@link Viewport} Celeritas culls against out of 1.8.9's camera matrices.
 * Port of Natron's {@code ViewportBuilder}.
 */
public final class ViewportBuilder {

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f clip = new Matrix4f();

    private final FrustumIntersection intersection = new FrustumIntersection();

    public Viewport create(FloatBuffer projectionBuffer, FloatBuffer modelViewBuffer,
                           double cameraX, double cameraY, double cameraZ) {
        projectionBuffer.rewind();
        modelViewBuffer.rewind();

        this.projection.set(projectionBuffer);
        this.modelView.set(modelViewBuffer);

        // GL matrices are column-major and so is JOML's set(FloatBuffer), so no transpose here.
        this.projection.mul(this.modelView, this.clip);
        this.intersection.set(this.clip);

        return new Viewport(new SimpleFrustum(this.intersection), new Vector3d(cameraX, cameraY, cameraZ));
    }
}
