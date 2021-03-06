#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

attribute vec4 a_position;
attribute vec4 a_color;
// size
attribute float a_size;

uniform float u_alpha;

uniform mat4 u_projModelView;
uniform vec3 u_camPos;
uniform float u_sizeFactor;

varying vec4 v_col;

void main() {
    vec3 pos = a_position.xyz - u_camPos;
    v_col = vec4(a_color.rgb, a_color.a * u_alpha );

    gl_Position = u_projModelView * vec4(pos, 0.0);
    gl_PointSize = a_size * u_sizeFactor;
}
