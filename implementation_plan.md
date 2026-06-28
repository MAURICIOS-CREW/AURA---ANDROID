# Plan de Implementación: Flujo de Autenticación y Restablecimiento de Contraseña para Android

Este plan describe cómo funciona el inicio de sesión y la renovación de tokens en el backend para la aplicación Android, y propone el diseño e implementación del flujo para restablecer la contraseña desde el móvil.

---

## 1. Inicio de Sesión (Login)

El backend ya cuenta con un controlador de inicio de sesión en [AuthController.php](file:///home/abdiel/projects/personal/Aura/Backend/app/Http/Controllers/Api/Mobile/AuthController.php) expuesto bajo la ruta `POST /api/mobile/auth/login`.

### Request (Estructura de la Petición)
*   **Método:** `POST`
*   **URL:** `/api/mobile/auth/login`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `Accept: application/json`
*   **Cuerpo (JSON):**
    ```json
    {
      "login": "usuario_o_email@example.com",
      "password": "mi_contraseña_secreta"
    }
    ```

### Validaciones en Backend
1.  **Parámetros:** Se validan que `login` y `password` sean obligatorios y cadenas de texto.
2.  **Búsqueda:** Busca al usuario por su `email` o `username`.
3.  **Contraseña:** Verifica el hash usando `Hash::check`.
4.  **Estado:** Confirma que el usuario esté activo (`is_active = true`).
5.  **Tipo de Usuario:** Valida que sea un usuario móvil (rol residente/guardia) usando el método `$user->isMobileUser()`.
6.  **Baneos:** Verifica que no exista un registro en la tabla `banned_users` para este usuario.

### Response (Respuesta Exitosa - 200 OK)
Retorna dos tokens generados mediante Laravel Sanctum:
```json
{
  "access_token": "1|abcdef123456789...",
  "refresh_token": "2|ghijk987654321...",
  "token_type": "bearer",
  "expires_in": 10800,
  "user": {
    "id": 1,
    "name": "Admin Dev",
    "username": "admin_dev",
    "email": "admin@example.com",
    "phone": "1234567890",
    "is_active": true,
    "role_id": 2,
    "role": {
      "id": 2,
      "name": "Residente",
      "slug": "residente"
    }
  }
}
```
*   `access_token`: Token de corta duración (3 horas) con la habilidad `access-api`. Debe enviarse en la cabecera `Authorization: Bearer <access_token>` en todas las llamadas a las API del móvil.
*   `refresh_token`: Token de larga duración (30 días) con la habilidad `issue-access-token`. Su único propósito es obtener un nuevo `access_token` cuando el actual expire.

---

## 2. Mantener la Sesión (Refresh Token)

Cuando el `access_token` caduca (después de 3 horas), cualquier petición de la app Android devolverá un error HTTP `401 Unauthorized`. La aplicación móvil Android debe interceptar este error y renovar el token automáticamente de manera transparente al usuario.

### Request (Petición de Renovación)
*   **Método:** `POST`
*   **URL:** `/api/mobile/auth/refresh`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `Accept: application/json`
    *   `Authorization: Bearer <refresh_token>`  <-- IMPORTANTE: Se envía el **refresh_token** aquí, no el access_token.

### Proceso en Backend
1.  El middleware `auth:api` (configurado con Sanctum en `config/auth.php`) valida que el token enviado sea válido y no haya expirado.
2.  El controlador verifica que el token actual tenga la habilidad `'issue-access-token'`. Si no la tiene (por ejemplo, si por error enviaron el `access_token`), responde con error `403 Forbidden`.
3.  Si es válido, genera un **nuevo** `access_token` de 3 horas.

### Response (Respuesta - 200 OK)
```json
{
  "access_token": "3|newaccess123456...",
  "token_type": "bearer",
  "expires_in": 10800
}
```
La aplicación Android almacena este nuevo `access_token` y reintenta la petición original que había fallado por el error 401.

---

## 3. Propuesta de Cambios: Flujo de Restablecimiento de Contraseña (Password Reset)

Como en el backend aún no se ha implementado el flujo de restablecimiento de contraseña para móviles, se propone agregar:
1.  Una migración para la tabla `password_reset_tokens`.
2.  Un endpoint para solicitar el restablecimiento (envía un código PIN de 6 dígitos al correo del usuario y lo guarda en la BD).
3.  Un endpoint para aplicar el restablecimiento de contraseña usando el PIN de 6 dígitos.

### Cambios Propuestos

#### [NEW] [2026_06_28_100000_create_password_reset_tokens_table.php](file:///home/abdiel/projects/personal/Aura/Backend/database/migrations/2026_06_28_100000_create_password_reset_tokens_table.php)
Creará la tabla para almacenar los PINs asociados al email.
```php
<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('password_reset_tokens', function (Blueprint $table) {
            $table->string('email')->primary();
            $table->string('token');
            $table->timestamp('created_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('password_reset_tokens');
    }
};
```

#### [MODIFY] [routes/api_mobile.php](file:///home/abdiel/projects/personal/Aura/Backend/routes/api_mobile.php)
Añadir rutas públicas para el flujo de restablecimiento:
```php
Route::post('/auth/forgot-password', [AuthController::class, 'forgotPassword']);
Route::post('/auth/reset-password', [AuthController::class, 'resetPassword']);
```

#### [MODIFY] [AuthController.php](file:///home/abdiel/projects/personal/Aura/Backend/app/Http/Controllers/Api/Mobile/AuthController.php)
Se implementarán dos nuevos métodos públicos: `forgotPassword` y `resetPassword`.

##### Método `forgotPassword(Request $request)`
1.  Valida que el `email` sea enviado y exista en la tabla `users`.
2.  Genera un código numérico aleatorio de 6 dígitos (ej. `rand(100000, 999999)`).
3.  Guarda en la tabla `password_reset_tokens` el par `email` y el código (encriptado con `Hash::make`).
4.  Envía un correo electrónico con el código. (Durante desarrollo, se guardará en `storage/logs/laravel.log` ya que `MAIL_MAILER=log` está configurado).
5.  Retorna una respuesta indicando que el código ha sido enviado.

**Request:**
```json
{
  "email": "usuario@example.com"
}
```

**Response (200 OK):**
```json
{
  "message": "Se ha enviado un código de verificación a tu correo electrónico."
}
```

##### Método `resetPassword(Request $request)`
1.  Valida que los campos `email`, `token` (el PIN de 6 dígitos), `password` y `password_confirmation` sean enviados y correctos.
2.  Busca el registro en `password_reset_tokens` usando el `email`.
3.  Verifica si el token ha expirado (por ejemplo, después de 15 minutos).
4.  Verifica si el PIN coincide usando `Hash::check($request->token, $record->token)`.
5.  Busca al usuario con dicho `email` y actualiza su contraseña.
6.  Elimina el token de `password_reset_tokens` para evitar reuso.

**Request:**
```json
{
  "email": "usuario@example.com",
  "token": "123456",
  "password": "nueva_contraseña_123",
  "password_confirmation": "nueva_contraseña_123"
}
```

**Response (200 OK):**
```json
{
  "message": "Tu contraseña ha sido restablecida exitosamente."
}
```

---

## Plan de Verificación

### Pruebas Automatizadas (Artisan)
*   Ejecutar la migración con `./sail artisan migrate` para comprobar que la base de datos se configure correctamente.
*   (Opcional) Podemos crear un test de integración para el AuthController (`AuthControllerTest.php`).

### Pruebas Manuales
1.  **Login:** Ejecutar petición con Postman / ThunderClient para verificar la obtención de `access_token` y `refresh_token`.
2.  **Renovación de token (Refresh):** Enviar petición a `/api/mobile/auth/refresh` pasando el `refresh_token` como Bearer token y comprobar que devuelve un nuevo `access_token`.
3.  **Solicitar PIN de reseteo:** Enviar petición a `/api/mobile/auth/forgot-password` y verificar en `Backend/storage/logs/laravel.log` el envío del correo y del código PIN de 6 dígitos.
4.  **Restablecer contraseña:** Enviar petición a `/api/mobile/auth/reset-password` usando el PIN correcto y validar que la contraseña del usuario cambie.
