#!/bin/sh

echo "⚙️ Генерация config.js с переменными окружения..."
envsubst < /usr/share/nginx/html/config.template.js > /usr/share/nginx/html/config.js

echo "🚀 Запуск nginx..."
exec nginx -g 'daemon off;'
