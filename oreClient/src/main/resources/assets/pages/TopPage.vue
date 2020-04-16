<template>
    <div>
        <div v-if="hasAnyAlerts" class="row">
            <div class="col-xs-12">
                <div v-for="level of levels">
                    <div v-if="alerts[level].length" class="alert alert-fade alert-dismissable" :class="levelToClass(level)" role="alert">
                        <button type="button" class="close" data-dismiss="alert" aria-label="Close" @click="$store.commit('dismissAllAlert', {level})">
                            <span aria-hidden="true">&times;</span>
                        </button>

                        <span v-if="alerts[level].length === 1">{{alerts[level][0].message}}</span>
                        <ul v-else-if="alerts[level].length">
                            <li v-for="alert in alerts[level]">{{ alert.message }}</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <button @click="$store.commit('addAlert', {level: 'info', message: 'Foobar'})">Add alert</button>
        <button @click="$store.commit('dismissAlert', {level: 'info', index: '0'})">Remove alert</button>

        <router-view></router-view>
    </div>
</template>

<script>
    import {mapState} from 'vuex'

    let levelClasses = {
        error: "alert-danger",
        success: "alert-success",
        info: "alert-info",
        warning: "alert-warning"
    }

    export default {
        computed: {
            levels() {
                return ['error', 'success', 'info', 'warning']
            },
            hasAnyAlerts() {
                return this.levels.some(level => this.alerts[level].length)
            },
            ...mapState(['alerts'])
        },
        methods: {
            levelToClass(level) {
                return levelClasses[level]
            }
        }
    }
</script>