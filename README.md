# Proyecto Web OO - SGI Minecraft 

Aplicación web (Spring Boot) para gestionar ítems, recetas de crafteo y perfiles de usuario, con autenticación vía Firebase.


## Credenciales de Firebase

1. Descarga el archivo `proyecto-weboo-firebase-adminsdk-fbsvc-ff60dbc800.json` desde este enlace:
   https://drive.google.com/drive/folders/1NXAdO63KF0k8-7Jxlc0kTTCfwpKoZCUr?usp=sharing
2. Coloca el archivo en la raíz del proyecto.

Por defecto, `application.properties` busca ese archivo con ese nombre en la raíz del proyecto (`firebase.credentials.path`), así que no necesitas configurar nada más si lo dejas ahí. Si prefieres guardarlo en otra ubicación, define la variable de entorno `GOOGLE_APPLICATION_CREDENTIALS` apuntando a la ruta del archivo.

## Ejecutar con Docker

```bash
docker-compose up --build
```
http://localhost:8080


## Credencial de admin
un admin puede hacer que un usuario normal sea admin
si quiere hacer otro superadmin en aplication.properties y que no importa si le quiten el rol de admin cuando se loggee vuelva a ser admin: 
admin.emails=${ADMIN_EMAILS:enmanuel.barrera2201@alumnos.ubiobio.cl,correo_del_profesor@ejemplo.com}

enmanuel.barrera2201@alumnos.ubiobio.cl
contraseña: 123456
