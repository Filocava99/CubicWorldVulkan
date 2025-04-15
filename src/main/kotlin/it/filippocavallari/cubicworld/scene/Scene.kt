package it.filippocavallari.cubicworld.scene

import it.filippocavallari.cubicworld.core.GameObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a scene with a collection of game objects.
 * Handles updating, rendering, and managing game objects.
 */
class Scene(val name: String) {
    
    // Map of game objects by ID
    private val gameObjects = ConcurrentHashMap<String, GameObject>()
    
    // Root game objects (those without parents)
    private val rootGameObjects = mutableListOf<GameObject>()
    
    /**
     * Adds a game object to the scene.
     * 
     * @param gameObject The game object to add
     */
    fun addGameObject(gameObject: GameObject) {
        gameObjects[gameObject.id] = gameObject
        rootGameObjects.add(gameObject)
    }
    
    /**
     * Gets a game object by ID.
     * 
     * @param id The ID of the game object to get
     * @return The game object, or null if not found
     */
    fun getGameObject(id: String): GameObject? {
        return gameObjects[id]
    }
    
    /**
     * Removes a game object from the scene.
     * 
     * @param id The ID of the game object to remove
     * @return True if the game object was removed, false if not found
     */
    fun removeGameObject(id: String): Boolean {
        val gameObject = gameObjects.remove(id) ?: return false
        rootGameObjects.remove(gameObject)
        return true
    }
    
    /**
     * Removes a game object from the scene.
     * 
     * @param gameObject The game object to remove
     * @return True if the game object was removed, false if not found
     */
    fun removeGameObject(gameObject: GameObject): Boolean {
        return removeGameObject(gameObject.id)
    }
    
    /**
     * Updates all game objects in the scene.
     * 
     * @param deltaTime Time since the last update
     */
    fun update(deltaTime: Float) {
        // Update all root game objects (they will update their children)
        rootGameObjects.forEach { it.update(deltaTime) }
    }
    
    /**
     * Gets all game objects in the scene.
     * 
     * @return A collection of all game objects
     */
    fun getAllGameObjects(): Collection<GameObject> {
        return gameObjects.values
    }
    
    /**
     * Gets all root game objects in the scene.
     * 
     * @return A list of all root game objects
     */
    fun getRootGameObjects(): List<GameObject> {
        return rootGameObjects.toList()
    }
    
    /**
     * Finds game objects by type.
     * 
     * @param clazz The class of game objects to find
     * @return A list of game objects of the specified type
     */
    fun <T : GameObject> findGameObjectsByType(clazz: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return gameObjects.values.filter { clazz.isInstance(it) } as List<T>
    }
    
    /**
     * Finds game objects by type using reified type parameter.
     * 
     * @return A list of game objects of the specified type
     */
    inline fun <reified T : GameObject> findGameObjectsByType(): List<T> {
        return findGameObjectsByType(T::class.java)
    }
    
    /**
     * Clears the scene by removing all game objects.
     */
    fun clear() {
        gameObjects.clear()
        rootGameObjects.clear()
    }
}