package it.filippocavallari.cubicworld.scene

/**
 * Manages scenes in the game.
 * Handles creating, loading, and switching between scenes.
 */
object SceneManager {
    
    // Map of scenes by name
    private val scenes = mutableMapOf<String, Scene>()
    
    // Active scene
    private var activeScene: Scene? = null
    
    /**
     * Creates a new scene with the specified name.
     * 
     * @param name The name of the scene
     * @return The created scene
     */
    fun createScene(name: String): Scene {
        val scene = Scene(name)
        scenes[name] = scene
        return scene
    }
    
    /**
     * Gets a scene by name.
     * 
     * @param name The name of the scene
     * @return The scene, or null if not found
     */
    fun getScene(name: String): Scene? {
        return scenes[name]
    }
    
    /**
     * Sets the active scene.
     * 
     * @param name The name of the scene to set as active
     * @return True if the scene was found and set as active, false otherwise
     */
    fun setActiveScene(name: String): Boolean {
        val scene = scenes[name] ?: return false
        activeScene = scene
        return true
    }
    
    /**
     * Sets the active scene.
     * 
     * @param scene The scene to set as active
     */
    fun setActiveScene(scene: Scene) {
        scenes[scene.name] = scene
        activeScene = scene
    }
    
    /**
     * Gets the active scene.
     * 
     * @return The active scene, or null if no scene is active
     */
    fun getActiveScene(): Scene? {
        return activeScene
    }
    
    /**
     * Removes a scene.
     * 
     * @param name The name of the scene to remove
     * @return True if the scene was removed, false if not found
     */
    fun removeScene(name: String): Boolean {
        if (activeScene?.name == name) {
            activeScene = null
        }
        return scenes.remove(name) != null
    }
    
    /**
     * Updates the active scene.
     * 
     * @param deltaTime Time since the last update
     */
    fun update(deltaTime: Float) {
        activeScene?.update(deltaTime)
    }
    
    /**
     * Checks if a scene exists.
     * 
     * @param name The name of the scene to check
     * @return True if the scene exists, false otherwise
     */
    fun hasScene(name: String): Boolean {
        return scenes.containsKey(name)
    }
    
    /**
     * Gets all scenes.
     * 
     * @return A collection of all scenes
     */
    fun getAllScenes(): Collection<Scene> {
        return scenes.values
    }
    
    /**
     * Clears all scenes.
     */
    fun clear() {
        scenes.clear()
        activeScene = null
    }
}