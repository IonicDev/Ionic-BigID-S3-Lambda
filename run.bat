for %%i in (target\Machina-BigID-S3-Lambda*.jar) do set jar_path= %%i
java -jar %jar_path% %*
