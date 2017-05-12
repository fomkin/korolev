/**
 * Created by flystyle on 24.01.17.
 */

const gulp = require('gulp');
const babel = require('gulp-babel');
const del = require('del');

const scripts = 'src/*.js';

gulp.task('clean', function() {
    return del(['build']);
});

gulp.task('watch', function() {
    gulp.watch(scripts, ['babel']);
});

gulp.task('babel', () => {
    return gulp.src(scripts)
        .pipe(babel({
            presets: ['es2015']
        }))
        .pipe(gulp.dest('dist'));
});

gulp.task('default', ['watch']);