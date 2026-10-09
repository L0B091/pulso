# Instrucciones de contenido

Este archivo lo lee Grok antes de generar los posts de cada día (00:00, hora de Argentina).
Editalo cuando quieras cambiar el tema, el tono o los rubros.

## Oferta (siempre la misma)
App Android para un negocio, sin servidor y sin cuota mensual. El cliente ve precios,
servicios o turnos en el teléfono, con los datos guardados en la app. No es un bot ni un
chat automático. Si hace falta un cambio, se actualiza la app.

## Público
Comercios y profesionales.

## Tema del día
Un rubro por día, rotando: peluquería, consultorio, taller, gimnasio, comercio de barrio,
estética, veterinaria, profesor particular.
<!-- Para fijar un rubro, escribilo acá: -->
Rubro fijo: (vacío = rotar)

## Los 9 posts
- 3 de ángulo "problema": dolores concretos de ese negocio.
- 3 de ángulo "incluye": qué ve el cliente (precios, servicios, turnos).
- 3 de ángulo "no-es": aclaraciones (no es bot, no es chat, no tiene cuota).

## Texto
- Español rioplatense, breve y concreto. De 2 a 4 frases.
- Sin jerga técnica, sin precios, sin promesas de hosting, métricas ni publicación automática.
- Sin enlaces: la app pega los enlaces fijos al final.
- Sin nombres de productos internos ni repositorios.

## Flyer
- Imagen 4:5 (1080x1350), JPG.
- Fondo azul marino #0B1F3A, acento naranja #FF7A1A, texto claro #F4F7FB.
- Una sola idea visual (un teléfono, un ícono del rubro). Como mucho 5 o 6 palabras.
- Sin logos de marcas reales, sin capturas de código.

## Formato de entrega
paquetes/pendientes/<id>/flyer.jpg y paquetes/pendientes/<id>/manifiesto.json:

```json
{
  "id": "2026-10-10-peluqueria-01",
  "estado": "pendiente",
  "tipo": "flyer",
  "redSugerida": null,
  "texto": "texto sin enlaces",
  "flyer": "flyer.jpg",
  "angulo": "problema",
  "creadoEn": "2026-10-10T00:05:00-03:00"
}
```

Rehacer: cuando llega un aviso con {"id": ...}, se rehace ese post en la misma carpeta
(mismo id, nuevo flyer y texto, "creadoEn" actualizado) y queda otra vez como pendiente.
