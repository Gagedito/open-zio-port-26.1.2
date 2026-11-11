/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import java.util.Optional;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ClientRenderPipelines {
   private static final DepthStencilState LEQUAL_DEPTH_STENCIL =
      new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
   private static final DepthStencilState NO_DEPTH_STENCIL =
      new DepthStencilState(CompareOp.ALWAYS_PASS, false);
   private static final DepthStencilState EQUAL_DEPTH_STENCIL =
      new DepthStencilState(CompareOp.EQUAL, true);
   private static final DepthStencilState LEQUAL_NO_WRITE_DEPTH_STENCIL =
      new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);
   private static final DepthStencilState NO_DEPTH_WRITE_STENCIL =
      new DepthStencilState(CompareOp.ALWAYS_PASS, true);
   private static final DepthStencilState EQUAL_NO_WRITE_DEPTH_STENCIL =
      new DepthStencilState(CompareOp.EQUAL, false);
   private static final ColorTargetState TRANSLUCENT_COLOR_TARGET =
      new ColorTargetState(BlendFunction.TRANSLUCENT);
   private static final ColorTargetState TRANSPARENT_COLOR_TARGET =
      new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL);
   // vanilla removed withBlend/withoutBlend/withDepthBias: those knobs moved into
   // ColorTargetState / DepthStencilState (26.1.2 render pipeline refactor)
   private static final ColorTargetState LIGHTNING_COLOR_TARGET =
      new ColorTargetState(BlendFunction.LIGHTNING);
   private static final ColorTargetState GLINT_COLOR_TARGET =
      new ColorTargetState(BlendFunction.GLINT);
   private static final ColorTargetState PREMULTIPLIED_ALPHA_COLOR_TARGET =
      new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
   private static final DepthStencilState DEPTH_WRITE_BIAS_STENCIL =
      new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, 1.0F, -500000.0F);
   private static final DepthStencilState EQUAL_NO_WRITE_BIAS_STENCIL =
      new DepthStencilState(CompareOp.EQUAL, false, 1.0F, -500000.0F);
   public static final Snippet POSITION_TEX_COLOR_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_TEXTURED_SNIPPET
      )
      .withVertexShader("core/zio_pos_tex_color")
      .withFragmentShader("core/zio_pos_tex_color")
      .buildSnippet();
   public static final Snippet GUI_LINES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, Mode.LINES)
      .buildSnippet();
   public static final Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_SNIPPET
      )
      .withVertexShader("core/zio_lines_smooth")
      .withFragmentShader("core/zio_lines_smooth")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, Mode.LINES)
      .withCull(false)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLE_STRIP_SNIPPET = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLE_STRIP)
      .buildSnippet();
   public static final RenderPipeline POSITION_TEX_COLOR = RenderPipeline.builder(
         POSITION_TEX_COLOR_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
      .withDepthStencilState(LEQUAL_DEPTH_STENCIL)
      .withLocation("pipeline/zio_pos_tex_color")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .build();
   // Fix upstream 2fec97aa (inconsistencias del font renderer mio): pipeline dedicado para la
   // fuente, derivado de GUI_TEXTURED_SNIPPET y sin cull, con sampler clamp-to-edge resuelto
   // via RenderSystem.getSamplerCache() (antes GUI_TEXTURED + textureSampler(linearFiltering)).
   public static final RenderPipeline FONT = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
      .withLocation("pipeline/zio_font")
      .withCull(false)
      .build();
   public static final RenderPipeline ENTITY_SOLID_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withLocation("pipeline/entity_solid_transparent")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withSampler("Sampler1")
      .build();
   public static final RenderPipeline ENTITY_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withLocation("pipeline/entity_cutout_no_cull_transparent")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withSampler("Sampler1")
      .withCull(false)
      .build();
   public static final RenderPipeline ARMOR_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withLocation("pipeline/armor_cutout_no_cull_transparent")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withSampler("Sampler1")
      .withCull(false)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .build();
   public static final RenderPipeline GUI_LINES = RenderPipeline.builder(
         GUI_LINES_SNIPPET
      )
      .withLocation("pipeline/gui_lines")
      .build();
   public static final RenderPipeline GUI_TRIANGLES = RenderPipeline.builder(
         GUI_TRIANGLES_SNIPPET
      )
      .withLocation("pipeline/gui_trigs")
      .build();
   public static final RenderPipeline GUI_TRIANGLE_STRIP = RenderPipeline.builder(
         GUI_TRIANGLE_STRIP_SNIPPET
      )
      .withLocation("pipeline/gui_trigs")
      .build();
   public static final RenderPipeline WORLD_FILL = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_pipeline")
      .withDepthStencilState(NO_DEPTH_WRITE_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_TAG_QUADS = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/zio_world_tag_quads")
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_FILL_LIGHTNING = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_light_pipeline")
      .withDepthStencilState(NO_DEPTH_WRITE_STENCIL)
      .withColorTargetState(LIGHTNING_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_FILL_DEPTH = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_depth_pipeline")
      .withDepthStencilState(LEQUAL_DEPTH_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_LINES = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_pipeline")
      .withDepthStencilState(NO_DEPTH_WRITE_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_LINES_LIGHTNING = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_light_pipeline")
      .withDepthStencilState(NO_DEPTH_WRITE_STENCIL)
      .withColorTargetState(LIGHTNING_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_LINES_DEPTH = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_pipeline")
      .withDepthStencilState(LEQUAL_DEPTH_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline WORLD_LINE_STRIP = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withVertexShader("core/position_color")
      .withFragmentShader("core/position_color")
      .withLocation("pipeline/world_lines_pipeline")
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.DEBUG_LINE_STRIP)
      .withDepthStencilState(NO_DEPTH_WRITE_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .build();
   public static final RenderPipeline ENTITY_SHADER = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withLocation("pipeline/entity_shader_pipeline")
      .withVertexShader("core/position")
      .withFragmentShader("core/position")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withCull(false)
      .build();
   public static final RenderPipeline BLIT_TRANSLUCENT = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   public static final RenderPipeline BLIT_LIGHTNING = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(LIGHTNING_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   public static final RenderPipeline BLIT_PREMULTIPLIED_ALPHA = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(PREMULTIPLIED_ALPHA_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   public static final RenderPipeline BLIT_NO_BLEND = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   public static final RenderPipeline AMBIENCE = RenderPipeline.builder(

      )
      .withLocation("pipeline/zio_ambience")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/zio_lightmap")
      .withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .build();
   public static final RenderPipeline OCCLUSION = RenderPipeline.builder(

      )
      .withLocation("pipeline/zio_occlusion")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/zio_occlusion")
      .withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .build();
   public static final RenderPipeline SHINE_GLINT = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET,
         RenderPipelines.FOG_SNIPPET,
         RenderPipelines.GLOBALS_SNIPPET
      )
      .withLocation("pipeline/zio_shine_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withSampler("Sampler0")
      .withCull(false)
      .withDepthStencilState(NO_DEPTH_STENCIL)
      .withColorTargetState(GLINT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX, Mode.QUADS)
      .build();
   public static final RenderPipeline NAMETAG_ITEM = RenderPipeline.builder(
         RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item")
      .withVertexShader(new Identifier("zio", "core/item_nolight"))
      .withFragmentShader("core/item")
      .withSampler("Sampler0")
      .withSampler("Sampler2")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withDepthStencilState(LEQUAL_NO_WRITE_DEPTH_STENCIL)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_CUTOUT = RenderPipeline.builder(
         RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item_cutout")
      // Replica 1:1 del ITEM_CUTOUT vanilla 26.1.2 (ITEM_SNIPPET + ALPHA_CUTOUT
      // 0.1F, sin color target propio => sin blend, depth DEFAULT).
      .withVertexShader(new Identifier("zio", "core/item_nolight"))
      .withFragmentShader("core/item")
      .withSampler("Sampler0")
      .withSampler("Sampler2")
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withDepthStencilState(DepthStencilState.DEFAULT)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item_translucent")
      // Replica del ITEM_TRANSLUCENT vanilla 26.1.2: ITEM_SNIPPET + ALPHA_CUTOUT
      // + blend TRANSLUCENT + depth LEQUAL sin write.
      .withVertexShader(new Identifier("zio", "core/item_nolight"))
      .withFragmentShader("core/item")
      .withSampler("Sampler0")
      .withSampler("Sampler2")
      .withVertexFormat(DefaultVertexFormat.ENTITY, Mode.QUADS)
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withDepthStencilState(LEQUAL_NO_WRITE_DEPTH_STENCIL)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_ENTITY = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item_entity")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withSampler("Sampler1")
      .withColorTargetState(ColorTargetState.DEFAULT)
      .withCull(false)
      .withDepthStencilState(DepthStencilState.DEFAULT)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_ENTITY_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item_entity_translucent")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withSampler("Sampler1")
      .withColorTargetState(TRANSLUCENT_COLOR_TARGET)
      .withCull(false)
      .withDepthStencilState(LEQUAL_NO_WRITE_DEPTH_STENCIL)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_GLINT = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET,
         RenderPipelines.FOG_SNIPPET,
         RenderPipelines.GLOBALS_SNIPPET
      )
      .withLocation("pipeline/zio_nametag_item_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withSampler("Sampler0")
      .withCull(false)
      .withDepthStencilState(EQUAL_NO_WRITE_DEPTH_STENCIL)
      .withColorTargetState(GLINT_COLOR_TARGET)
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX, Mode.QUADS)
      .build();

   public static boolean isGlint(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.GLINT
         || pipeline == SHINE_GLINT
         || pipeline == NAMETAG_ITEM_GLINT;
   }

   public static RenderPipeline getNameTagItemPipeline(RenderPipeline pipeline) {
      if (pipeline == RenderPipelines.ENTITY_TRANSLUCENT_CULL) {
         return NAMETAG_ITEM;
      }
      if (pipeline == RenderPipelines.GLINT) {
         return NAMETAG_ITEM_GLINT;
      }
      if (pipeline == RenderPipelines.ENTITY_SOLID
         || pipeline == RenderPipelines.ENTITY_CUTOUT
         || pipeline == RenderPipelines.ENTITY_CUTOUT
         || pipeline == RenderPipelines.ENTITY_CUTOUT_Z_OFFSET
         || pipeline == RenderPipelines.ENTITY_CUTOUT_DISSOLVE) {
         return NAMETAG_ITEM_ENTITY;
      }
      if (pipeline == RenderPipelines.ENTITY_TRANSLUCENT) {
         return NAMETAG_ITEM_ENTITY_TRANSLUCENT;
      }
      if (pipeline == RenderPipelines.ITEM_CUTOUT) {
         return NAMETAG_ITEM_CUTOUT;
      }
      if (pipeline == RenderPipelines.ITEM_TRANSLUCENT) {
         return NAMETAG_ITEM_TRANSLUCENT;
      }
      return null;
   }
}
