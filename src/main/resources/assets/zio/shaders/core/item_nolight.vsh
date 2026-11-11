#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    // Experimento tono apagado nametags: SIN minecraft_mix_light y SIN
    // sample_lightmap. El estado vanilla (Lighting ITEMS_FLAT, lightmap del
    // mundo, pose mundo del billboard) multiplica el color por factores
    // < 1 que en 1.21.11 daban 1.0. La GUI vanilla 26.1.2 hornea los items
    // a planos (GuiItemAtlas) y nunca pasa por core/item en vivo, asi que
    // este shader replica el resultado plano.
    vertexColor = Color;

    texCoord0 = UV0;
}
