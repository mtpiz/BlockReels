# Detectors are looked up reflectively by nothing today, but keep the accessibility
# service entry point regardless — it is instantiated by the framework, not by us.
-keep class app.blockreels.service.BlockReelsService { *; }
