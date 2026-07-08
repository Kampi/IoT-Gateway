if(CONFIG_APP_HEARTBEAT_LED)
  zephyr_library()
  zephyr_library_sources(${CMAKE_CURRENT_LIST_DIR}/../src/heartbeat_led.c)
endif()
