for %%i in (target\Ionic-BigID-S3-Lambda*.jar) do set jar_path= %%i
java -jar %jar_path% %*
