package it.filippocavallari.cubicworld.renderer

/**
 * Represents a material for rendering meshes.
 * Defines how a mesh is rendered, including textures and shaders.
 */
class Material(
    val shader: Shader,
    val diffuseTexture: Texture? = null,
    val normalTexture: Texture? = null,
    val specularTexture: Texture? = null
) {
    // Material properties
    var ambientColor = floatArrayOf(0.2f, 0.2f, 0.2f)
    var diffuseColor = floatArrayOf(0.8f, 0.8f, 0.8f)
    var specularColor = floatArrayOf(1.0f, 1.0f, 1.0f)
    var shininess = 32.0f
    
    // Custom properties for shader uniforms
    private val floatProperties = mutableMapOf<String, Float>()
    private val intProperties = mutableMapOf<String, Int>()
    private val vec2Properties = mutableMapOf<String, FloatArray>() // size 2
    private val vec3Properties = mutableMapOf<String, FloatArray>() // size 3
    private val vec4Properties = mutableMapOf<String, FloatArray>() // size 4
    private val mat4Properties = mutableMapOf<String, FloatArray>() // size 16
    
    /**
     * Sets a float property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property
     */
    fun setFloat(name: String, value: Float) {
        floatProperties[name] = value
    }
    
    /**
     * Gets a float property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getFloat(name: String, defaultValue: Float = 0f): Float {
        return floatProperties[name] ?: defaultValue
    }
    
    /**
     * Sets an int property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property
     */
    fun setInt(name: String, value: Int) {
        intProperties[name] = value
    }
    
    /**
     * Gets an int property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getInt(name: String, defaultValue: Int = 0): Int {
        return intProperties[name] ?: defaultValue
    }
    
    /**
     * Sets a vec2 property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property as a float array of size 2
     */
    fun setVec2(name: String, value: FloatArray) {
        require(value.size == 2) { "Vec2 property must have 2 components" }
        vec2Properties[name] = value
    }
    
    /**
     * Gets a vec2 property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getVec2(name: String, defaultValue: FloatArray = floatArrayOf(0f, 0f)): FloatArray {
        return vec2Properties[name] ?: defaultValue
    }
    
    /**
     * Sets a vec3 property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property as a float array of size 3
     */
    fun setVec3(name: String, value: FloatArray) {
        require(value.size == 3) { "Vec3 property must have 3 components" }
        vec3Properties[name] = value
    }
    
    /**
     * Gets a vec3 property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getVec3(name: String, defaultValue: FloatArray = floatArrayOf(0f, 0f, 0f)): FloatArray {
        return vec3Properties[name] ?: defaultValue
    }
    
    /**
     * Sets a vec4 property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property as a float array of size 4
     */
    fun setVec4(name: String, value: FloatArray) {
        require(value.size == 4) { "Vec4 property must have 4 components" }
        vec4Properties[name] = value
    }
    
    /**
     * Gets a vec4 property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getVec4(name: String, defaultValue: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)): FloatArray {
        return vec4Properties[name] ?: defaultValue
    }
    
    /**
     * Sets a mat4 property for shader uniforms.
     * 
     * @param name The name of the property
     * @param value The value of the property as a float array of size 16
     */
    fun setMat4(name: String, value: FloatArray) {
        require(value.size == 16) { "Mat4 property must have 16 components" }
        mat4Properties[name] = value
    }
    
    /**
     * Gets a mat4 property.
     * 
     * @param name The name of the property
     * @param defaultValue The default value to return if the property is not found
     * @return The value of the property, or the default value if not found
     */
    fun getMat4(name: String, defaultValue: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }): FloatArray {
        return mat4Properties[name] ?: defaultValue
    }
    
    /**
     * Binds the material for rendering.
     */
    fun bind() {
        shader.bind()
        
        // Bind textures
        diffuseTexture?.bind(0)
        normalTexture?.bind(1)
        specularTexture?.bind(2)
        
        // Set texture uniforms
        if (diffuseTexture != null) {
            shader.setInt("diffuseTexture", 0)
        }
        
        if (normalTexture != null) {
            shader.setInt("normalTexture", 1)
        }
        
        if (specularTexture != null) {
            shader.setInt("specularTexture", 2)
        }
        
        // Set material properties
        shader.setVec3("material.ambient", ambientColor)
        shader.setVec3("material.diffuse", diffuseColor)
        shader.setVec3("material.specular", specularColor)
        shader.setFloat("material.shininess", shininess)
        
        // Set custom properties
        floatProperties.forEach { (name, value) -> shader.setFloat(name, value) }
        intProperties.forEach { (name, value) -> shader.setInt(name, value) }
        vec2Properties.forEach { (name, value) -> shader.setVec2(name, value) }
        vec3Properties.forEach { (name, value) -> shader.setVec3(name, value) }
        vec4Properties.forEach { (name, value) -> shader.setVec4(name, value) }
        mat4Properties.forEach { (name, value) -> shader.setMat4(name, value) }
    }
    
    /**
     * Unbinds the material after rendering.
     */
    fun unbind() {
        // Unbind textures
        diffuseTexture?.unbind()
        normalTexture?.unbind()
        specularTexture?.unbind()
        
        // Unbind shader
        shader.unbind()
    }
}