<template>
    <ProjectList :owner="user" :offset="(page - 1) * limit" :limit="limit"
                 @prevPage="page--" @nextPage="page++" @jumpToPage="page = $event" :use-vue-router="false"></ProjectList>
</template>

<script>
    import ProjectList from "./../components/ProjectList";

    export default {
        components: {
            ProjectList
        },
        props: {
            user: {
                type: String,
                required: true
            }
        },
        data: function() {
            return {
                page: 1,
                limit: 5
            }
        }
    }
</script>
