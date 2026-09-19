package redacted.client.util.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

// Two textureless conical kasa hats in head-local pixel coords. Head cube is addBox(-4,-8,-4,8,8,8),
// and +Y is DOWN in model space, so the head's top face is the y=-8 plane and a hat on the crown
// lives at y<=-8, climbing by going MORE negative. Centered at x=z=0. Stepped concentric slabs
// approximate a cone; rendered color-only so UVs don't matter.
public class ModelHat extends ModelBase {
    public final ModelRenderer samurai; // wide flat straw brim
    public final ModelRenderer sensei;  // taller, steeper, narrower

    public ModelHat() {
        this.textureWidth = 64;
        this.textureHeight = 64;

        // SAMURAI: wide low cone, brim flush with the crown, rises ~4px
        this.samurai = new ModelRenderer(this, 0, 0);
        this.samurai.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.samurai.addBox(-8.0F, -9.0F, -8.0F, 16, 1, 16);
        this.samurai.addBox(-6.0F, -10.0F, -6.0F, 12, 1, 12);
        this.samurai.addBox(-4.0F, -11.0F, -4.0F, 8, 1, 8);
        this.samurai.addBox(-2.0F, -12.0F, -2.0F, 4, 1, 4);
        this.samurai.addBox(-1.0F, -13.0F, -1.0F, 2, 1, 2);

        // SENSEI: narrower base, steeper/taller cone (~6px)
        this.sensei = new ModelRenderer(this, 0, 0);
        this.sensei.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.sensei.addBox(-6.0F, -9.0F, -6.0F, 12, 1, 12);
        this.sensei.addBox(-5.0F, -10.0F, -5.0F, 10, 1, 10);
        this.sensei.addBox(-4.0F, -11.0F, -4.0F, 8, 1, 8);
        this.sensei.addBox(-3.0F, -12.0F, -3.0F, 6, 1, 6);
        this.sensei.addBox(-2.0F, -13.0F, -2.0F, 4, 1, 4);
        this.sensei.addBox(-1.0F, -14.0F, -1.0F, 2, 1, 2);
        this.sensei.addBox(-1.0F, -15.0F, -1.0F, 1, 1, 1);
    }
}
