package org.evosuite.seeding;

import java.io.File;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.testcase.TestCase;
import org.evosuite.utils.GenericClass;
import org.evosuite.utils.LoggingUtils;

public class ObjectPoolManager extends ObjectPool {

	private static final long serialVersionUID = 6287216639197977371L;

	private static ObjectPoolManager instance = null;
	
	private ObjectPoolManager() {
		initialisePool();
	}
	
	public static ObjectPoolManager getInstance() {
		if(instance == null)
			instance = new ObjectPoolManager();
		return instance;
	}
	
	public void addPool(ObjectPool pool) {
		for(GenericClass clazz : pool.getClasses()) {
			Set<TestCase> tests = pool.getSequences(clazz);
			if(this.pool.containsKey(clazz))
				this.pool.get(clazz).addAll(tests);
			else
				this.pool.put(clazz, tests);
		}
	}
	
	public void initialisePool() {
		if(!Properties.OBJECT_POOLS.isEmpty()) {
			String[] poolFiles = Properties.OBJECT_POOLS.split(File.pathSeparator);
			if(poolFiles.length > 1)
				LoggingUtils.getEvoLogger().info("* Reading object pools:");
			else
				LoggingUtils.getEvoLogger().info("* Reading object pool:");
			for(String fileName : poolFiles) {
                logger.info("Adding object pool from file {}", fileName);
				ObjectPool pool = ObjectPool.getPoolFromFile(fileName);
				if(pool==null){
                    logger.error("Failed to load object from {}", fileName);
				} else {
                    LoggingUtils.getEvoLogger().info(" - Object pool {}: {} sequences for {} classes", fileName, pool.getNumberOfSequences(), pool.getNumberOfClasses());
					addPool(pool);
				}
			}
			if(logger.isDebugEnabled()) {
				for(GenericClass key : pool.keySet()) {
                    logger.debug("Have sequences for {}: {}", key, pool.get(key).size());
				}
			}
		}
	}
	
	public void reset() {
		pool.clear();
	}

}
