package redacted.client.util.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

// One ender-dragon wing (+ child tip), lifted verbatim from ModelDragon's wing geometry so the
// UVs land on the same 256x256 dragon sheet. The layer renders it twice mirrored for both sides.
public class ModelWings extends ModelBase {
    public final ModelRenderer wing;
    public final ModelRenderer wingTip;

    public ModelWings() {
        this.textureWidth = 256;
        this.textureHeight = 256;

        // texture offsets live on the model, keyed by "<model>.<box>" (same as ModelDragon)
        this.setTextureOffset("wing.bone", 112, 88);
        this.setTextureOffset("wing.skin", -56, 88);
        this.setTextureOffset("wingtip.bone", 112, 136);
        this.setTextureOffset("wingtip.skin", -56, 144);

        this.wing = new ModelRenderer(this, "wing");
        this.wing.setRotationPoint(-12.0F, 5.0F, 2.0F);
        this.wing.addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8);
        this.wing.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);

        this.wingTip = new ModelRenderer(this, "wingtip");
        this.wingTip.setRotationPoint(-56.0F, 0.0F, 0.0F);
        this.wingTip.addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4);
        this.wingTip.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);
        this.wing.addChild(this.wingTip);
    }
}
