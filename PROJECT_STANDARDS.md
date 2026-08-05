# Guía de Estándares de Diseño y Desarrollo para IA (AURA Android)

Este documento define las reglas de arquitectura, patrones de diseño y estándares de interfaz que **TODAS LAS INTELIGENCIAS ARTIFICIALES Y DESARROLLADORES** deben seguir estrictamente al implementar o modificar cualquier código en este repositorio.

---

## 1. Principios SOLID y Limpieza de Código

- **SOLID Estricto**: Todo nuevo módulo, Activity, Fragment, ViewModel o Adapter debe basarse en arquitectura SOLID.
  - **SRP (Single Responsibility Principle)**: Cada clase debe tener una sola responsabilidad bien delimitada.
  - **OCP (Open/Closed Principle)**: Extiende funcionalidades mediante componentes reutilizables sin modificar la lógica base.
  - **LSP / ISP / DIP**: Depender de abstracciones (ej. `ApiClient`, interfaces de Listener/Callback) y evitar dependencias acopladas.
- **Prohibido el Código Spaghetti y Nidos de If-Else**:
  - Queda **estrictamente prohibido** escribir bloques `if-else` anidados profundos. Usar `when`, cláusulas de guarda (`guard` / early returns), o patrones Polimórficos / State.
  - Mantener las funciones cortas, legibles y bien nombradas y antetodo muy bien tipadas en todo. Preservar siempre todos los comentarios y docstrings existentes.

---

## 2. Componentes de UI Reutilizables y Tarjetas (`AuraCardBtn`)

- **Contenedor de Tarjetas (`AuraCardBtn`)**:
  - **NUNCA** usar un `ConstraintLayout` o `CardView` genérico como raíz de un item de lista o tarjeta interactiva si representa una card interactiva. Usar siempre `<com.mexadev.aura.ui.common.AuraCardBtn>` como elemento raíz en los XML de items (`item_*.xml`).
  - `AuraCardBtn` provee la animación de toque con física (efecto spring/bounce en `onTouchEvent`), sombra sutil, bordes estandarizados (`aura_border_light`), bordes redondeados (16dp) y fondo blanco limpio (`aura_white`).
- **Inputs Reutilizables**:
  - Utilizar estilos estandarizados de inputs (`TextInputLayout` / `TextInputEditText` o componentes custom del proyecto).
  - Nunca hardcodear padding, bordes o colores de enfocado; usar siempre las propiedades de tema.
- **Switches Estandarizados**:
  - Utilizar `MaterialSwitch` configurado con física táctil y animación de rebote (`applySwitchPhysics`).

---

## 3. Manejo de Asincronía y Loaders en Botones

- **Loaders en Acciones Asíncronas**:
  - Todo botón que ejecute un proceso asíncrono (guardar, editar, contratar, marcar como finalizado, eliminar, etc.) debe:
    1. Deshabilitar el botón (`isEnabled = false`).
    2. Mostrar un indicador de carga (`ProgressBar` visible, ocultando el texto del botón o superponiéndolo).
    3. Al finalizar la petición (éxito o error), restaurar el estado del botón (`isEnabled = true`) y ocultar el `ProgressBar`.
  - Esto evita clicks dobles y llamadas repetidas al backend.

---

## 4. Carga Asíncrona con Skeleton Loaders

- **Skeleton Loading Obligatorio**:
  - Toda vista o lista que cargue datos asincrónicamente debe mostrar un **Skeleton Loader** durante la petición inicial.
  - Utilizar los layouts de skeleton (`item_*_skeleton.xml`, `layout_detail_skeleton.xml`).
  - Usar `PreferencesManager` para recordar el último conteo de items y evitar cambios bruscos de tamaño.
  - Animar el skeleton con pulso de transparencia suave (`ObjectAnimator.ofFloat(..., "alpha", 1f, 0.4f, 1f)`).

---

## 5. Vistas Detalladas y Transiciones Compartidas (Shared Element Transitions)

- **Transiciones Compartidas en Vistas Detalladas**:
  - Cada navegación a una pantalla de detalle (Activity o Fragment) debe utilizar **MaterialContainerTransform** o **ActivityOptionsCompat.makeSceneTransitionAnimation** con elementos compartidos (`transitionName`).
  - La actividad de detalle debe declarar en `onCreate`:
    ```kotlin
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    ```
  - Usar `postponeEnterTransition()` y `post { startPostponedEnterTransition() }` para evitar parpadeos (flicker) al entrar.
  - Usar `requireActivity().setExitSharedElementCallback(...)` en la lista/fragment origen para remapear los elementos en el retorno.

---

## 6. Actualizaciones de RecyclerView Atómicas y Sin Parpadeos

- **Prohibido el Repintado Completo**:
  - **NUNCA** llamar a `notifyDataSetChanged()` si se puede evitar.
  - Utilizar `ListAdapter` con `DiffUtil.ItemCallback` para que RecyclerView compare los datos y solo anime/repinte el elemento modificado de forma atómica.
  - Si un item cambia o se actualiza en el detalle, al regresar actualizar solo ese elemento o enviar la lista a `submitList(...)`.
  - Si los datos no cambiaron, la UI debe permanecer inmutable sin destellos ni parpadeos.

---

## 7. Mensajes de Error/Éxito en Cintilla (`BannerManager`) y Confirmaciones

- **Mensajes de Respuesta (Cintilla/Banner)**:
  - Los mensajes de feedback visual (error o éxito) NO deben ser Toasts flotantes genéricos para operaciones principales.
  - Utilizar la cintilla estandarizada superior manejada con `BannerManager` (`binding.errorBanner` / `binding.successBanner`).
- **Diálogos de Confirmación**:
  - Toda acción destructiva o de confirmación (eliminar, cancelar, confirmar pago, etc.) debe reutilizar el componente `ConfirmBottomSheetFragment.newInstance(...)`.

---

## 8. Recursos de Cadenas (Localization) y Recursos de Color

- **Prohibido Cadenas Hardcodeadas (No Hardcoded Strings)**:
  - Queda **estrictamente prohibido** colocar cadenas literales en archivos Kotlin o XML.
  - Toda cadena de texto visible para el usuario debe registrarse en `res/values/strings.xml` (`R.string.*`).
- **Paleta de Colores Semántica**:
  - Utilizar los colores definidos en `colors.xml`: `@color/aura_primary`, `@color/aura_white`, `@color/aura_success`, `@color/aura_error`, `@color/aura_warning`, `@color/aura_info`, `@color/aura_text_primary`, `@color/aura_border_light`, etc.
  - NUNCA usar valores hexadecimales directos (`#FF0000`) en archivos XML o Kotlin.

---

## 9. Compilación y Verificación de Código

- **Verificación al Finalizar**:
  - Al completar un cambio o función, es obligatorio ejecutar la compilación/build del proyecto (ej. `./gradlew assembleDebug` o comprobación de Gradle) para garantizar que no existan errores de compilación, warnings por deprecación ni malas prácticas.
