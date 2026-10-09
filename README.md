# Pulso

Consola de publicación para Android. Grok deja los posts (flyer + texto) en este repo,
la app los baja, les pega los enlaces fijos, te avisa a la hora que elegiste y abre la
pantalla Enviar con todo armado. **Vos tocás Enviar. La app nunca publica sola.**

No usa APIs de redes, ni login de Meta, ni WebView, ni métricas, ni servidor propio.
WhatsApp no es una red de publicación: solo es uno de los enlaces fijos.

## Estructura del repo

```
app/                          Código de la app (Kotlin, ViewBinding, Room, WorkManager)
contenido/prompt.md           Instrucciones de contenido que lee Grok cada noche
paquetes/pendientes/<id>/     Un post: flyer.jpg + manifiesto.json
```

`manifiesto.json`:

```json
{
  "id": "prueba-001",
  "estado": "pendiente",
  "tipo": "flyer",
  "redSugerida": null,
  "texto": "texto sin enlaces",
  "flyer": "flyer.jpg",
  "angulo": "problema | incluye | no-es",
  "creadoEn": "2026-10-09T00:00:00-03:00"
}
```

## Compilar

Requisitos: JDK 17 y Android SDK con `platforms;android-34` y `build-tools;34.0.0`.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Instalar en el teléfono (con depuración USB): `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Primer uso

1. **Permisos**: la primera vez la app pide notificaciones y, si hace falta, alarmas exactas.
   Sin alarmas exactas el aviso puede llegar unos minutos tarde.
2. **Ajustes** (ícono arriba a la derecha):
   - Enlaces fijos: WhatsApp, Comentar en Instagram y Comentar en Facebook.
     Son obligatorios: si falta uno, la app no arma el envío y te dice cuál falta.
   - Token de GitHub (se guarda cifrado). Creá un token *fine-grained* en
     GitHub → Settings → Developer settings → Fine-grained tokens, con acceso solo al repo
     `L0B091/pulso` y permiso **Contents: Read-only**.
   - Repositorio (`L0B091/pulso`) y rama (`main`).
   - Aviso de rehacer: URL, clave y nombre del header (por defecto `Authorization`, que se
     manda como `Bearer <clave>`). La URL y la clave también se guardan cifradas.
   - *Cargar paquete de prueba*: crea un flyer de prueba local, ya listo, sin GitHub.
3. **Cuentas**: en Instagram y Facebook agregá destinos (nombre + URL). Instagram es perfil;
   Facebook puede ser perfil o grupo. Sin destino, un post no se agenda.

## Uso diario

- **Día** se abre solo la primera vez que abrís la app cada día (y desde el ícono de
  calendario). Elegís 1, 2 o 3 posteos y, para cada uno, la hora y el destino. La app no
  propone horas: sin hora o sin destino, ese posteo no se agenda. Si no bajó nada nuevo
  hoy, muestra "Sin contenido nuevo".
- **Galería**: todos los flyers bajados y los clips que agregaste, acumulados. Deslizá hacia
  abajo para sincronizar. En cada uno: *Usar* (pasa a listo), *Rehacer* (solo flyers: manda
  el aviso para que se haga uno nuevo) y *Descartar*. Solo lo que está *listo* se agenda.
  *Agregar clip* te deja elegir un `.mp4` del teléfono; queda solo en la app, nunca va a GitHub.
- **Orquestador**: alterna flyer y clip si tenés clips listos, y no repite el mismo flyer ni
  el mismo ángulo en el día. Antes de cada hora sincroniza (WorkManager, unos 15 minutos antes).
  A la hora manda una notificación; al tocarla se abre Enviar con todo armado. Si no enviaste,
  repite el aviso a los 10 minutos; si seguís sin enviar, el post pasa a *saltado*.
- **Enviar**: muestra la red, el flyer o clip (4:5), el texto con los 3 enlaces al final (los
  clips llevan solo los enlaces) y el destino. Al tocar Enviar se abre el menú de compartir de
  Android (o directo la app oficial si está instalada) y el texto queda copiado por si la app
  no lo pega. Al volver se marca *enviado* y se abre la URL del destino. Android no avisa si
  publicaste de verdad, por eso queda el botón *Deshacer*.

Estados: `pendiente`, `listo`, `enviado`, `saltado`.

## Limpieza

Al abrir la app y en cada sync se borran del teléfono los archivos descartados y lo que no
usaste en más de 30 días. El repo no se toca.

## Sin token

Sin token (o sin conexión) la app sigue funcionando con lo que ya bajó y con tus clips.
